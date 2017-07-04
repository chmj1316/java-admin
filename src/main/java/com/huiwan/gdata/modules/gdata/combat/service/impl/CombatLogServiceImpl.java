package com.huiwan.gdata.modules.gdata.combat.service.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jetty.util.ajax.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSONObject;
import com.huiwan.gdata.common.utils.pagination.Paginator;
import com.huiwan.gdata.common.utils.pagination.PaginatorResult;
import com.huiwan.gdata.modules.gdata.base.GDataDao;
import com.huiwan.gdata.modules.gdata.base.charset.bean.QueryCommBean;
import com.huiwan.gdata.modules.gdata.combat.entity.CombatAttr;
import com.huiwan.gdata.modules.gdata.combat.entity.CombatLog;
import com.huiwan.gdata.modules.gdata.combat.service.CombatLogService;
import com.huiwan.gdata.modules.sys.entity.Dict;
import com.huiwan.gdata.modules.sys.service.IDictService;
import com.huiwan.gdata.modules.sys.service.IGameDictService;

@Service
public class CombatLogServiceImpl implements CombatLogService {
	Logger log = LoggerFactory.getLogger(getClass());

	@Autowired
	private GDataDao gdataDao;
	@Autowired
	private IDictService dictService;
	@Autowired
	private IGameDictService gameDictService;

	@Override
	public PaginatorResult getPaginatorList(Paginator paginator, QueryCommBean bean) {
		PaginatorResult result = new PaginatorResult();
		// 获取总记录数
		result.setTotal(getLogTotal(bean));
		// 参数
		List<Object> paramArray = new LinkedList<Object>();
		// 条件组装
		StringBuffer sqlWhere = getSQLString(bean);// 完成
		StringBuffer sql = new StringBuffer(512);
		// sql.append(
		// "SELECT to_char(time,'YYYY-MM-DD HH24:MI:SS') dt,cont->>'add_hp'
		// add_hp,cont->>'dungeon_id' dungeon_id ");

		sql.append("SELECT id,server_id,file,to_char(time,'YYYY-MM-DD HH24:MI:SS') dt,cont->>'uuid' uuid,cont cont ");

		sql.append(" FROM zl_log_info ");
		sql.append(sqlWhere);
		// 排序
		if (StringUtils.isNotBlank(paginator.getSort())) {
			if ("logType".equals(paginator.getSort())) {
				sql.append(" order by ");
				sql.append(" flag ");
				sql.append(paginator.getOrder());
			}
			if ("dt".equals(paginator.getSort())) {
				sql.append(" order by ");
				sql.append(" dt ");
				sql.append(paginator.getOrder());
			}
		} else {
			sql.append(" order by dt desc");
		}
		// 分页
		sql.append(" LIMIT ");
		sql.append(paginator.getLimit());
		sql.append(" offset ");
		sql.append(paginator.getOffset());

		log.info("sql:>>>\n{}\n param={}", sql.toString(), paramArray.toArray());
		List<CombatLog> data = gdataDao.selectObjectList(sql.toString(), rowMapper);

		Map<String, String> dicts = dictService.getByTypeMaps("_file_types");
		Map<String, String> servers_type = dictService.getByTypeMaps("servers_type");
		
		//技能列表 
		Map<String,String> zl_skill_type=	gameDictService.getByTypeMaps("zl_skill_type");
		//监听列表 
		Map<String,Dict> zl_event_type=	gameDictService.getByTypeMapsDicts("zl_event_type");
		//效果概率
		Map<String,String> zl_effect_type=	gameDictService.getByTypeMaps("zl_effect_type");
		//BUFF
		Map<String,Dict> zl_buff_types=	gameDictService.getByTypeMapsDicts("zl_buff_types");
		
		// 转换中文
		if (data != null && data.size() > 0) {
			for (CombatLog log : data) {
				//有些需要做判单
//				log.setFileSrc(log.getFile());
				// 转换文件名
				if (dicts.containsKey(log.getFile())) {
					log.setFileName(dicts.get(log.getFile()));
				}
				// 转换服务器
				if (servers_type.containsKey(String.valueOf(log.getServerId()))) {
					log.setServer(servers_type.get(String.valueOf(log.getServerId())));
				} else {
					log.setServer(String.valueOf(log.getServerId()));
				}
				//技能名转换
				if(StringUtils.isBlank(log.getCont())){
					continue;
				}
				if("skill".equals(log.getFile())){
					JSONObject jsonCont=(JSONObject) JSONObject.parse(log.getCont());
					if(!jsonCont.containsKey("skill_id")||StringUtils.isBlank(jsonCont.getString("skill_id"))){
						continue;
					}
					String skill_id=jsonCont.getString("skill_id");
					String skill_name=zl_skill_type.get(skill_id);
					jsonCont.put("skill_name", skill_name);
					log.setCont(jsonCont.toJSONString());
				}
				//监听转换
				if("opport".equals(log.getFile())){
					JSONObject jsonCont=(JSONObject) JSONObject.parse(log.getCont());
					if(!jsonCont.containsKey("opports")||StringUtils.isBlank(jsonCont.getString("opports"))){
						continue;
					}
					String key=jsonCont.getString("opports");//是一个数组，需转换
					String arrs_str=key.replace("[", "").replace("]", "");
					String[] arrs=arrs_str.split(",");
					if(arrs!=null&&arrs.length>0){
						List<Dict> d_temp=new ArrayList<>();
						for(String arr_key:arrs){
							if(zl_event_type.containsKey(arr_key)){
								Dict dict=zl_event_type.get(arr_key);
								d_temp.add(dict);
							}else{//没有找到，则显示为id
								Dict dict=new Dict();
								dict.setValue(arr_key);
								dict.setName(arr_key);
								dict.setRemake(arr_key);
							}
						}
						jsonCont.put("opports_josn", d_temp);
					}
					log.setCont(jsonCont.toJSONString());
				}
				//效果转换
				if("effect".equals(log.getFile())){
					JSONObject jsonCont=(JSONObject) JSONObject.parse(log.getCont());
					if(!jsonCont.containsKey("effect_id")||StringUtils.isBlank(jsonCont.getString("effect_id"))){
						continue;
					}
					String key=jsonCont.getString("effect_id");
					if(zl_effect_type.containsKey(key)){
						String skill_name=zl_effect_type.get(key);
						jsonCont.put("effect_Prob", skill_name);
					}else{
						jsonCont.put("effect_Prob", key);
					}
					log.setCont(jsonCont.toJSONString());
				}
				
				//BUFF处理效果  把file转换 add_buf 添加Buf del_buf 删除Buf  buf_expire Buf过期删除
				if("add_buf".equals(log.getFile())||"del_buf".equals(log.getFile())||"buf_expire".equals(log.getFile())){
					log.setFileName("BUFF处理效果");
//					log.setFile("buff_all");
					//查询buffstatus
					JSONObject jsonCont=(JSONObject) JSONObject.parse(log.getCont());
//					if(!jsonCont.containsKey("add_bufs")||StringUtils.isBlank(jsonCont.getString("add_bufs"))){
//						continue;
//					}
					String key="";
					
					if(jsonCont.containsKey("add_bufs")){
						 key=jsonCont.getString("add_bufs");//是一个数组，需转换
					}
					if(jsonCont.containsKey("del_buff_ids")){
						 key=jsonCont.getString("del_buff_ids");//是一个数组，需转换
					}
					if(jsonCont.containsKey("del_buff_ids")){
						 key=jsonCont.getString("del_buff_ids");//是一个数组，需转换
					}
					String arrs_str=key.replace("[", "").replace("]", "");
					String[] arrs=arrs_str.split(",");
					if(arrs!=null&&arrs.length>0){
						List<Dict> d_temp=new ArrayList<>();
						for(String arr_key:arrs){
							if(zl_buff_types.containsKey(arr_key)){
								Dict dict=zl_buff_types.get(arr_key);
								d_temp.add(dict);
							}else{//没有找到，则显示为id
								Dict dict=new Dict();
								dict.setValue(arr_key);
								dict.setName(arr_key);
								dict.setRemake(arr_key);
							}
						}
						jsonCont.put("buff_all_json", d_temp);
					}
					log.setCont(jsonCont.toJSONString());
					
				}
				
				
				
			}
		}

		result.setRows(data);
		return result;
	}

	private RowMapper<CombatLog> rowMapper = new RowMapper<CombatLog>() {
		@Override
		public CombatLog mapRow(ResultSet rs, int rowNum) throws SQLException {
			CombatLog bean = new CombatLog();
			bean.setId(rs.getLong("id"));
			bean.setServerId(rs.getInt("server_id"));
			bean.setDt(rs.getString("dt"));
			bean.setUuid(rs.getString("uuid"));
			bean.setFile(rs.getString("file"));
			bean.setCont(rs.getString("cont"));
			return bean;
		}
	};

	/**
	 * 获取总记录数
	 * 
	 * @param vo
	 * @return
	 */
	public int getLogTotal(QueryCommBean vo) {
		// 参数
		List<Object> paramArray = new LinkedList<Object>();
		// 条件组装
		StringBuffer sqlWhere = getSQLString(vo);// 完成
		StringBuffer sql = new StringBuffer(512);
		sql.append("select  count(*) ");
		sql.append(" FROM zl_log_info ");
		sql.append(sqlWhere);
		log.info("sql:>>>\n{}\n param={}", sql.toString(), paramArray.toArray());
		int total = gdataDao.selectForRows(sql.toString());
		return total;
	}

	private StringBuffer getSQLString(QueryCommBean vo) {
		// 条件语句
		StringBuffer sbWhere = new StringBuffer();

		if (vo != null) {
			if (StringUtils.isNotBlank(vo.getFile())) {
				sbWhere.append(" and file='");
				sbWhere.append(vo.getFile());
				sbWhere.append("'");
			}

			// 服务器
			if (vo.getServer() != null && vo.getServer() > 0) {
				sbWhere.append(" and server_id=");
				sbWhere.append(vo.getServer());
			}
			// 日期
			if (StringUtils.isNotBlank(vo.getDt1())) {// 如果不为空，
				sbWhere.append(" and time>='");
				sbWhere.append(vo.getDt1());
				sbWhere.append("'");
			}

			if (StringUtils.isNotBlank(vo.getDt2())) {// 如果不为空，
				sbWhere.append(" and time<='");
				sbWhere.append(vo.getDt2());
				// sbWhere.append(" 23:59:59'");
				sbWhere.append("'");
			}

			// 副本
			if (StringUtils.isNotBlank(vo.getCopyId())) {// 如果不为空，
				sbWhere.append(" and cont->>'copyId'='");
				sbWhere.append(vo.getCopyId());
				sbWhere.append("'");
			}

			// uuid
			if (StringUtils.isNotBlank(vo.getType())) {// 如果不为空，
				sbWhere.append(" and cont->>'uuid'='");
				sbWhere.append(vo.getType());
				sbWhere.append("'");
			}
			//
			// if (StringUtils.isNotBlank(vo.getCommQueryString())) {// 见听对象
			// sbWhere.append(" and cont->>copyId='");
			// sbWhere.append(vo.getCommQueryString());
			// sbWhere.append("'");
			// }

		}
		if (StringUtils.isNotBlank(sbWhere.toString())) {
			// 删除 and 前4位
			sbWhere.delete(0, 5);
			sbWhere.insert(0, " where ");
		}
		// 转数组
		return sbWhere;
	}

	@Override
	public CombatLog getDetail(Integer id) {
		String sql = "select  id,server_id,file,to_char(time,'YYYY-MM-DD HH24:MI:SS') dt,cont->>'uuid' uuid,cont cont from zl_log_info where id="
				+ id;
		log.info("sql:>>>\n{}", sql.toString());
		CombatLog data = gdataDao.selectObject(sql.toString(), rowMapper);
		return data;
	}

	@Override
	public List<Dict> getObjTypes(int type, String severId) {
		if (type == 1) {
			// String sql="SELECT DISTINCT(cont->>'uuid') uuid,time FROM
			// zl_log_info order by time desc LIMIT 10";
			String sql = "select cont->>'uuid' uuid,cont->>'name' namea,cont->>'actor_type' actory from zl_log_info ";
			if (StringUtils.isNotBlank(severId)) {
				sql += " where server_id='" + severId + "'";
			}
			sql += " order by id desc  LIMIT 1000";
			log.info("sql:>>>\n{}", sql.toString());
			List<Dict> data = gdataDao.selectObjectList(sql.toString(), type_rowMapper);
			// 排序去重

			Map<String, Dict> data_new = new LinkedHashMap<String, Dict>();

			if (data != null && data.size() > 0) {
				for (Dict dict : data) {
					data_new.put(dict.getValue(), dict);
					if (data_new.size() >= 10) {
						break;
					}
				}
			}
			List<Dict> data_result = new ArrayList<Dict>();
			for (Entry<String, Dict> entry : data_new.entrySet()) {
				data_result.add(entry.getValue());
			}
			return data_result;
		}
		if (type == 2) {
			String sql = "SELECT DISTINCT (cont->>'dungeon_id') arg1 FROM(select * from zl_log_info ";
			if (StringUtils.isNotBlank(severId)) {
				sql += " where server_id='" + severId + "'";
			}
			sql += " order by id desc  LIMIT 5000 )  as t1 LIMIT 10";
			log.info("sql:>>>\n{}", sql.toString());
			List<Dict> data = gdataDao.selectObjectList(sql.toString(), type_rowMapper_cpoy);
			return data;
		}
		return null;
	}

	private RowMapper<Dict> type_rowMapper_cpoy = new RowMapper<Dict>() {
		@Override
		public Dict mapRow(ResultSet rs, int rowNum) throws SQLException {
			Dict bean = new Dict();
			bean.setValue(rs.getString("arg1"));
			return bean;
		}
	};

	private RowMapper<Dict> type_rowMapper = new RowMapper<Dict>() {
		@Override
		public Dict mapRow(ResultSet rs, int rowNum) throws SQLException {
			Dict bean = new Dict();
			bean.setValue(rs.getString("uuid"));
			// actor_type 角色类型(human=玩家, mon=怪物, partner=人物的招唤物)
			String type = rs.getString("actory");
			String name=rs.getString("namea");
			if (StringUtils.isBlank(type)) {
				type = "";
			}
			if (StringUtils.isBlank(name)) {
				name = "";
			}
			if (type.equals("human")) {
				type = "玩家";
			}
			if (type.equals("mon")) {
				type = "怪物";
			}
			if (type.equals("partner")) {
				type = "人物的招唤物";
			}

			String var = type + "-" + name + "-" + rs.getString("uuid");
			bean.setName(var);
			// bean.setType(rs.getString("actory"));
			return bean;
		}
	};

	/**
	 * 获取最大时间
	 * 
	 * @param bean
	 * @return
	 */
	public String getMaxTime(QueryCommBean bean) {
		// 参数
		List<Object> paramArray = new LinkedList<Object>();
		// 条件组装
		StringBuffer sqlWhere = getSQLString(bean);// 完成
		StringBuffer sql = new StringBuffer(512);
		sql.append("SELECT to_char(MAX(time),'YYYY-MM-DD HH24:MI:SS') tdata FROM zl_log_info ");
		sql.append(sqlWhere);
		log.debug("sql:>>>\n{}\n param={}", sql.toString(), paramArray.toArray());
		String data = gdataDao.selectObject(sql.toString(), str_rowMapper);
		return data;
	}

	private RowMapper<String> str_rowMapper = new RowMapper<String>() {
		@Override
		public String mapRow(ResultSet rs, int rowNum) throws SQLException {
			return rs.getString("tdata");
		}
	};

	@Override
	public CombatAttr getAttrs(QueryCommBean bean) {
		bean.setFile("attrs");
		// 获取最大的时间
		String maxDate = getMaxTime(bean);
		if (StringUtils.isBlank(maxDate)) {
			return new CombatAttr();
		}

		// 参数
		List<Object> paramArray = new LinkedList<Object>();
		// 条件组装
		StringBuffer sqlWhere = getSQLString(bean);// 完成
		StringBuffer sql = new StringBuffer(512);
		sql.append("select * from zl_log_info ");
		sql.append(sqlWhere);
		sql.append(" and time='");
		sql.append(maxDate);
		sql.append("'");
		sql.append(" LIMIT 1");
		log.info("sql:>>>\n{}\n param={}", sql.toString(), paramArray.toArray());
		CombatAttr data = gdataDao.selectObject(sql.toString(), rowMapper_attrs);
		
		//取下一条
//		select * from zl_log_info  where file='attrs' and cont->>'uuid'='10000114' and time<'2017-06-29 17:32:13' order by time desc LIMIT 1
		CombatAttr dif=getDifAttrs(bean,maxDate);
		if(dif!=null&&StringUtils.isNotBlank(dif.getCont())){
			//对比数据
			data.setContDif(dif.getCont());
		}
		data.setTime(maxDate);
		return data;
	}
	
	//取下一条
//	select * from zl_log_info  where file='attrs' and cont->>'uuid'='10000114' and time<'2017-06-29 17:32:13' order by time desc LIMIT 1
	public CombatAttr getDifAttrs(QueryCommBean bean,String time) {
		bean.setFile("attrs");
		// 获取最大的时间
		String maxDate = getMaxTime(bean);
		if (StringUtils.isBlank(maxDate)) {
			return new CombatAttr();
		}

		// 参数
		List<Object> paramArray = new LinkedList<Object>();
		// 条件组装
		StringBuffer sqlWhere = getSQLString(bean);// 完成
		StringBuffer sql = new StringBuffer(512);
		sql.append("select * from zl_log_info ");
		sql.append(sqlWhere);
		sql.append(" and time<'");
		sql.append(maxDate);
		sql.append("'");
		sql.append(" order by time desc LIMIT 1");
		log.info("sql:>>>\n{}\n param={}", sql.toString(), paramArray.toArray());
		CombatAttr data = gdataDao.selectObject(sql.toString(), rowMapper_attrs);
		data.setTime(maxDate);
		return data;
	}
	

	private RowMapper<CombatAttr> rowMapper_attrs = new RowMapper<CombatAttr>() {
		@Override
		public CombatAttr mapRow(ResultSet rs, int rowNum) throws SQLException {
			CombatAttr bean = new CombatAttr();
			bean.setCont(rs.getString("cont"));
			// bean.setUuid(rs.getString("uuid"));
			// bean.setName(rs.getString(columnIndex));
			return bean;
		}
	};

}
