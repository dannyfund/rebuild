/*
Copyright (c) REBUILD <https://getrebuild.com/> and/or its owners. All rights reserved.

rebuild is dual-licensed under commercial and open source licenses (GPLv3).
See LICENSE and COMMERCIAL in the project root for license information.
*/

$(document).ready(function () {
  const entity = $urlp('entity')

  const $btn = $('.btn-primary').click(function () {
    const fieldLabel = $val('#fieldLabel'),
      type = $val('#type'),
      comments = $val('#comments'),
      refEntity = $val('#refEntity'),
      refClassification = $val('#refClassification'),
      stateClass = $val('#stateClass') || 'com.rebuild.core.support.state.HowtoState'
    if (!fieldLabel) {
      return RbHighbar.create($L('请输入字段名称'))
    }

    if ((type === 'REFERENCE' || type === 'N2NREFERENCE') && !refEntity) {
      return RbHighbar.create('请选择引用实体')
    } else if (type === 'CLASSIFICATION' && !refClassification) {
      return RbHighbar.create('请选择分类数据')
    } else if (type === 'STATE' && !stateClass) {
      return RbHighbar.create('请选择状态类 (Enum)')
    }

    const data = {
      entity: entity,
      label: fieldLabel,
      type: type,
      comments: comments,
      refEntity: refEntity,
      refClassification: refClassification,
      stateClass: stateClass,
    }

    $btn.button('loading')
    $.post('/admin/entity/field-new', JSON.stringify(data), function (res) {
      $btn.button('reset')
      if (res.error_code === 0) {
        if ($val('#saveAndNew')) {
          RbHighbar.success($L('字段已添加'))
          $('#fieldLabel, #comments').val('')
          $('#type').val('TEXT').trigger('change')
          $('#fieldLabel').focus()

          // @see `field-new.html`
          parent && parent.loadFields && parent.loadFields()
          // @see `form-design.js`
          parent && parent.add2Layout && parent.add2Layout(res.data)
        } else {
          parent.location.href = `${rb.baseUrl}/admin/entity/${entity}/field/${res.data}`
        }
      } else {
        RbHighbar.error(res.error_msg)
      }
    })
  })

  let referenceLoaded = false
  let classificationLoaded = false
  $('#type').change(function () {
    parent.RbModal.resize()

    $('.J_dt-REFERENCE, .J_dt-N2NREFERENCE, .J_dt-CLASSIFICATION, .J_dt-STATE').addClass('hide')
    const dt = $(this).val()
    $('.J_dt-' + dt).removeClass('hide')

    if (dt === 'REFERENCE' || dt === 'N2NREFERENCE') {
      if (referenceLoaded === false) {
        referenceLoaded = true
        $.get('/admin/entity/entity-list?detail=true&bizz=false', (res) => {
          const _data = res.data || []
          _data.push({ entityName: 'User', entityLabel: $L('用户') })
          _data.push({ entityName: 'Department', entityLabel: $L('部门') })
          // _data.push({ entityName: 'Team', entityLabel: $L('团队') })
          // _data.push({ entityName: 'Role', entityLabel: $L('角色') })

          $(_data).each(function () {
            $(`<option value="${this.entityName}">${this.entityLabel}${this.mainEntity ? ' (' + $L('明细实体') + ')' : ''}</option>`).appendTo('#refEntity')
          })
          if (_data.length === 0) $(`<option value="">${$L('无可用实体')}</option>`).appendTo('#refEntity')
        })
      }
    } else if (dt === 'CLASSIFICATION') {
      if (classificationLoaded === false) {
        classificationLoaded = true
        $.get('/admin/metadata/classification/list', (res) => {
          let hasData = false
          $(res.data).each(function () {
            if (!this[2]) {
              $(`<option value="${this[0]}">${this[1]}</option>`).appendTo('#refClassification')
              hasData = true
            }
          })
          if (!hasData) $(`<option value="">${$L('无可用分类数据')}</option>`).appendTo('#refClassification')
        })
      }
    } else if (dt === 'STATE') {
      // NOOP
    }
  })

  const designType = $urlp('type')
  if (designType) {
    $('#type').val(designType).trigger('change').parents('.form-group').addClass('hide')
    $('#saveAndNew').attr('checked', true).parents('.form-group').addClass('hide')
  }

  $('#fieldLabel')[0].focus()
})
