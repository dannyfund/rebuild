/*
rebuild - Building your business-systems freely.
Copyright (C) 2019 devezhao <zhaofang123@gmail.com>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package com.rebuild.server.configuration.portals;

import com.alibaba.fastjson.JSON;
import com.rebuild.server.Application;
import com.rebuild.server.configuration.ConfigEntry;
import com.rebuild.server.configuration.ConfigManager;

import cn.devezhao.persist4j.engine.ID;

/**
 * @author devezhao-mbp zhaofang123@gmail.com
 * @since 2019/06/04
 */
public class ChartManager implements ConfigManager<ID> {

	public static final ChartManager instance = new ChartManager();
	private ChartManager() { }
	
	/**
	 * @param chartid
	 * @return
	 */
	public ConfigEntry getChart(ID chartid) {
		final String ckey = "Chart-" + chartid;
		ConfigEntry entry = (ConfigEntry) Application.getCommonCache().getx(ckey);
		if (entry != null) {
			return entry.clone();
		}
		
		Object[] o = Application.createQueryNoFilter(
				"select title,chartType,config,createdBy from ChartConfig where chartId = ?")
				.setParameter(1, chartid)
				.unique();
		if (o == null) {
			return null;
		}
		
		entry = new ConfigEntry()
				.set("title", o[0])
				.set("type", o[1])
				.set("config", JSON.parse((String) o[2]))
				.set("createdBy", o[3]);
		Application.getCommonCache().putx(ckey, entry);
		return entry.clone();
	}
	
	
	@Override
	public void clean(ID cacheKey) {
		Application.getCommonCache().evict("Chart-" + cacheKey);
	}
}
