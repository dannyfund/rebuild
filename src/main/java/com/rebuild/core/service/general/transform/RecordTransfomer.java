/*
Copyright (c) REBUILD <https://getrebuild.com/> and/or its owners. All rights reserved.

rebuild is dual-licensed under commercial and open source licenses (GPLv3).
See LICENSE and COMMERCIAL in the project root for license information.
*/

package com.rebuild.core.service.general.transform;

import cn.devezhao.persist4j.Entity;
import cn.devezhao.persist4j.Field;
import cn.devezhao.persist4j.Record;
import cn.devezhao.persist4j.engine.ID;
import com.alibaba.fastjson.JSONObject;
import com.rebuild.core.Application;
import com.rebuild.core.RebuildException;
import com.rebuild.core.configuration.ConfigurationException;
import com.rebuild.core.metadata.EntityHelper;
import com.rebuild.core.metadata.MetadataHelper;
import com.rebuild.core.service.TransactionManual;
import com.rebuild.core.service.query.FilterRecordChecker;
import com.rebuild.core.support.SetUser;
import com.rebuild.core.support.general.FieldValueCompatibleConversion;
import org.apache.commons.lang.StringUtils;
import org.springframework.transaction.TransactionStatus;

import java.util.*;

/**
 * 转换记录
 *
 * @author devezhao
 * @since 2020/10/27
 */
public class RecordTransfomer extends SetUser {

    final private Entity targetEntity;
    final private JSONObject transConfig;

    /**
     * @param targetEntity
     */
    public RecordTransfomer(Entity targetEntity, JSONObject transConfig) {
        this.targetEntity = targetEntity;
        this.transConfig = transConfig;
    }

    /**
     * @param sourceRecordId
     * @return
     * @see FilterRecordChecker
     */
    public boolean checkFilter(ID sourceRecordId) {
        JSONObject useFilter = transConfig.getJSONObject("useFilter");
        return new FilterRecordChecker(useFilter).check(sourceRecordId);
    }

    /**
     * @param sourceRecordId
     * @return
     * @see #checkFilter(ID)
     */
    public ID transform(ID sourceRecordId) {
        // 事务
        TransactionStatus tx = TransactionManual.newTransaction();

        try {

            JSONObject fieldsMapping = transConfig.getJSONObject("fieldsMapping");
            if (fieldsMapping == null || fieldsMapping.isEmpty()) {
                throw new ConfigurationException("Invalid config of transform : " + transConfig);
            }

            final Entity sourceEntity = MetadataHelper.getEntity(sourceRecordId.getEntityCode());
            final ID newId = transformRecord(sourceEntity, targetEntity, fieldsMapping, sourceRecordId, null);

            // 明细

            JSONObject fieldsMappingDetail = transConfig.getJSONObject("fieldsMappingDetail");
            if (fieldsMappingDetail != null && !fieldsMappingDetail.isEmpty()) {

                // 获取

                Entity sourceDetailEntity = sourceEntity.getDetailEntity();
                Field sourceDtf = MetadataHelper.getDetailToMainField(sourceDetailEntity);

                String sql = String.format(
                        "select %s from %s where %s = '%s'",
                        sourceDetailEntity.getPrimaryField().getName(), sourceDetailEntity.getName(), sourceDtf.getName(), sourceRecordId);
                Object[][] details = Application.createQueryNoFilter(sql).array();

                // 创建

                Entity targetDetailEntity = targetEntity.getDetailEntity();
                Map<String, Object> map = null;
                if (details.length > 0) {
                    Field targetDtf = MetadataHelper.getDetailToMainField(targetDetailEntity);

                    map = new HashMap<>();
                    map.put(targetDtf.getName(), newId);
                }

                for (Object[] o : details) {
                    transformRecord(sourceDetailEntity, targetDetailEntity, fieldsMappingDetail, (ID) o[0], map);
                }
            }

            // 回填

            String fillbackField = transConfig.getString("fillbackField");
            if (StringUtils.isNotBlank(fillbackField) && MetadataHelper.checkAndWarnField(sourceEntity, fillbackField)) {
                Record updateSource = EntityHelper.forUpdate(sourceRecordId, getUser(), false);
                updateSource.setID(fillbackField, newId);
                Application.getEntityService(sourceEntity.getEntityCode()).update(updateSource);
            }

            TransactionManual.commit(tx);
            return newId;

        } catch (Exception ex) {
            TransactionManual.rollback(tx);
            throw ex;
        }
    }

    private ID transformRecord(
            Entity sourceEntity, Entity targetEntity, JSONObject fieldsMapping,
            ID sourceRecordId, Map<String, Object> defaultValue) {

        Record target = EntityHelper.forNew(targetEntity.getEntityCode(), getUser());

        if (defaultValue != null) {
            for (Map.Entry<String, Object> e : defaultValue.entrySet()) {
                target.setObjectValue(e.getKey(), e.getValue());
            }
        }

        List<String> validFields = checkAndWarnFields(sourceEntity, fieldsMapping.values());
        String querySource = String.format(
                "select %s from %s where %s = '%s'",
                StringUtils.join(validFields, ","), sourceEntity.getName(),
                sourceEntity.getPrimaryField().getName(), sourceRecordId);
        Record source = Application.createQueryNoFilter(querySource).record();

        for (Map.Entry<String, Object> e : fieldsMapping.entrySet()) {
            String tf = e.getKey();
            String sf = (String) e.getValue();

            Object value = source.getObjectValue(sf);
            if (value != null) {
                Object compatibleValue = new FieldValueCompatibleConversion(
                        sourceEntity.getField(sf), targetEntity.getField(tf)).convert(value);
                target.setObjectValue(tf, compatibleValue);
            }
        }

        target = Application.getEntityService(targetEntity.getEntityCode()).create(target);
        return target.getPrimary();
    }

    private List<String> checkAndWarnFields(Entity entity, Collection<?> fieldsName) {
        List<String> valid = new ArrayList<>();
        for (Object field : fieldsName) {
            if (MetadataHelper.checkAndWarnField(entity, (String) field)) {
                valid.add((String) field);
            }
        }
        return valid;
    }
}
