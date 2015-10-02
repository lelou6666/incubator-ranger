/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.ranger.tagsync.source.atlas;

import org.apache.atlas.notification.entity.EntityNotification;
import org.apache.atlas.typesystem.api.Entity;
import org.apache.atlas.typesystem.api.Trait;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ranger.plugin.model.RangerPolicy;
import org.apache.ranger.plugin.model.RangerServiceResource;
import org.apache.ranger.plugin.model.RangerTag;
import org.apache.ranger.plugin.model.RangerTagDef;
import org.apache.ranger.plugin.util.ServiceTags;
import org.apache.ranger.tagsync.process.TagSyncConfig;

import java.util.*;

class AtlasNotificationMapper {
	private static final Log LOG = LogFactory.getLog(AtlasNotificationMapper.class);

	public static final String ENTITY_TYPE_HIVE_DB = "hive_db";
	public static final String ENTITY_TYPE_HIVE_TABLE = "hive_table";
	public static final String ENTITY_TYPE_HIVE_COLUMN = "hive_column";

	public static final String RANGER_TYPE_HIVE_DB = "database";
	public static final String RANGER_TYPE_HIVE_TABLE = "table";
	public static final String RANGER_TYPE_HIVE_COLUMN = "column";

	public static final String ENTITY_ATTRIBUTE_QUALIFIED_NAME = "qualifiedName";
	public static final String QUALIFIED_NAME_FORMAT_DELIMITER_STRING = "\\.";


	private static Properties properties = null;

	public static ServiceTags processEntityNotification(EntityNotification entityNotification, Properties props) {

		ServiceTags ret = null;
		properties = props;

		try {
			if (isEntityMappable(entityNotification.getEntity())) {
				ret = createServiceTags(entityNotification);
			} else {
				LOG.info("Ranger not interested in Entity Notification for entity-type " + entityNotification.getEntity().getTypeName());
			}
		} catch (Exception exception) {
			LOG.error("createServiceTags() failed!! ", exception);
		}
		return ret;
	}

	static private boolean isEntityMappable(Entity entity) {
		boolean ret = false;

		String entityTypeName = entity.getTypeName();

		if (StringUtils.isNotBlank(entityTypeName)) {
			if (StringUtils.equals(entityTypeName, ENTITY_TYPE_HIVE_DB) ||
					StringUtils.equals(entityTypeName, ENTITY_TYPE_HIVE_TABLE) ||
					StringUtils.equals(entityTypeName, ENTITY_TYPE_HIVE_COLUMN)) {
				ret = true;
			}
		}
		return ret;
	}

	static private ServiceTags createServiceTags(EntityNotification entityNotification) throws Exception {

		ServiceTags ret = null;

		EntityNotification.OperationType opType = entityNotification.getOperationType();
		Entity entity = entityNotification.getEntity();

		String opName = entityNotification.getOperationType().name();
		switch (opType) {
			case ENTITY_CREATED: {
				ret = getServiceTags(entity, opType);
				break;
			}
			case ENTITY_UPDATED: {
				ret = handleEntityUpdate(entity);
				break;
			}
			case TRAIT_ADDED: {
				ret = getServiceTags(entity, opType);
				break;
			}
			case TRAIT_DELETED: {
				ret = handleTraitDelete(entity);
				break;
			}
			default:
				LOG.error("Unknown notification received. Will not be handled, notificationType=" + opName);
		}

		return ret;
	}

	static private ServiceTags getServiceTags(Entity entity, EntityNotification.OperationType opType) throws Exception {
		ServiceTags ret = null;


		List<RangerServiceResource> serviceResources = new ArrayList<RangerServiceResource>();

		RangerServiceResource serviceResource = getServiceResource(entity, opType);
		serviceResources.add(serviceResource);

		Map<Long, RangerTag> tags = getTags(entity);

		Map<Long, RangerTagDef> tagDefs = getTagDefs(tags, EntityNotification.OperationType.ENTITY_CREATED);

		Map<Long, List<Long>> resourceIdToTagIds = null;

		if (MapUtils.isNotEmpty(tags)) {
			resourceIdToTagIds = new HashMap<Long, List<Long>>();

			List<Long> tagList = new ArrayList<Long>();
			for (Map.Entry<Long, RangerTag> entry : tags.entrySet()) {
				tagList.add(entry.getKey());
			}
			resourceIdToTagIds.put(1L, tagList);
		}

		ret = new ServiceTags();

		ret.setOp(ServiceTags.OP_ADD_OR_UPDATE);
		ret.setServiceName(serviceResource.getServiceName());
		ret.setServiceResources(serviceResources);
		ret.setTagDefinitions(tagDefs);
		ret.setTags(tags);
		ret.setResourceToTagIds(resourceIdToTagIds);

		return ret;
	}


	static private RangerServiceResource getServiceResource(Entity entity, EntityNotification.OperationType opType) throws Exception {

		RangerServiceResource ret = null;

		Map<String, RangerPolicy.RangerPolicyResource> elements = null;
		String serviceName = null;

		if (opType == EntityNotification.OperationType.ENTITY_CREATED) {

			elements = new HashMap<String, RangerPolicy.RangerPolicyResource>();

			String[] components = getQualifiedNameComponents(entity);
			// components should contain qualifiedName, instanceName, dbName, tableName, columnName in that order


			String entityTypeName = entity.getTypeName();

			String instanceName, dbName, tableName, columnName;

			if (components.length > 1) {
				instanceName = components[1];
				serviceName = getServiceName(instanceName, entityTypeName);
			}

			if (StringUtils.equals(entityTypeName, ENTITY_TYPE_HIVE_DB)) {
				if (components.length > 2) {
					dbName = components[2];
					RangerPolicy.RangerPolicyResource dbPolicyResource = new RangerPolicy.RangerPolicyResource(dbName);
					elements.put(RANGER_TYPE_HIVE_DB, dbPolicyResource);

				} else {
					LOG.error("invalid qualifiedName for HIVE_DB, qualifiedName=" + components[0]);
				}
			} else if (StringUtils.equals(entityTypeName, ENTITY_TYPE_HIVE_TABLE)) {
				if (components.length > 3) {
					dbName = components[2];
					tableName = components[3];
					RangerPolicy.RangerPolicyResource dbPolicyResource = new RangerPolicy.RangerPolicyResource(dbName);
					elements.put(RANGER_TYPE_HIVE_DB, dbPolicyResource);
					RangerPolicy.RangerPolicyResource tablePolicyResource = new RangerPolicy.RangerPolicyResource(tableName);
					elements.put(RANGER_TYPE_HIVE_TABLE, tablePolicyResource);
				} else {
					LOG.error("invalid qualifiedName for HIVE_TABLE, qualifiedName=" + components[0]);
				}
			} else if (StringUtils.equals(entityTypeName, ENTITY_TYPE_HIVE_COLUMN)) {
				if (components.length > 4) {
					dbName = components[2];
					tableName = components[3];
					columnName = components[4];
					RangerPolicy.RangerPolicyResource dbPolicyResource = new RangerPolicy.RangerPolicyResource(dbName);
					elements.put(RANGER_TYPE_HIVE_DB, dbPolicyResource);
					RangerPolicy.RangerPolicyResource tablePolicyResource = new RangerPolicy.RangerPolicyResource(tableName);
					elements.put(RANGER_TYPE_HIVE_TABLE, tablePolicyResource);
					RangerPolicy.RangerPolicyResource columnPolicyResource = new RangerPolicy.RangerPolicyResource(columnName);
					elements.put(RANGER_TYPE_HIVE_COLUMN, columnPolicyResource);
				} else {
					LOG.error("invalid qualifiedName for HIVE_COLUMN, qualifiedName=" + components[0]);
				}

			}
		}

		ret = new RangerServiceResource();
		ret.setGuid(entity.getId().getGuid());
		ret.setId(1L);
		ret.setServiceName(serviceName);
		ret.setResourceElements(elements);

		return ret;
	}

	static private Map<Long, RangerTag> getTags(Entity entity) {
		Map<Long, RangerTag> ret = null;

		Map<String, ? extends Trait> traits = entity.getTraits();

		if (MapUtils.isNotEmpty(traits)) {
			ret = new HashMap<Long, RangerTag>();
			long index = 1;

			for (Map.Entry<String, ? extends Trait> entry : traits.entrySet()) {
				String traitName = entry.getKey();
				Trait trait = entry.getValue();

				Map<String, Object> attrValues = trait.getValues();

				Map<String, String> tagAttrValues = new HashMap<String, String>();

				for (Map.Entry<String, Object> attrValueEntry : attrValues.entrySet()) {
					String attrName = attrValueEntry.getKey();
					Object attrValue = attrValueEntry.getValue();
					try {
						String strValue = String.class.cast(attrValue);
						tagAttrValues.put(attrName, strValue);
					} catch (ClassCastException exception) {
						LOG.error("Cannot cast attribute-value to String, skipping... attrName=" + attrName);
					}
				}

				RangerTag tag = new RangerTag();

				tag.setGuid(entity.getId().getGuid() + "-" + traitName);
				tag.setType(traitName);
				tag.setAttributes(tagAttrValues);

				ret.put(index++, tag);
			}
		}

		return ret;
	}

	static private Map<Long, RangerTagDef> getTagDefs(Map<Long, RangerTag> tags, EntityNotification.OperationType opType) {

		Map<Long, RangerTagDef> ret = null;

		if (opType == EntityNotification.OperationType.ENTITY_CREATED || opType == EntityNotification.OperationType.TRAIT_ADDED) {
			if (MapUtils.isNotEmpty(tags)) {
				ret = new HashMap<Long, RangerTagDef>();
				for (Map.Entry<Long, RangerTag> entry : tags.entrySet()) {
					RangerTagDef tagDef = new RangerTagDef();
					tagDef.setName(entry.getValue().getType());
					tagDef.setId(entry.getKey());
					ret.put(entry.getKey(), tagDef);
				}
			}
		}

		return ret;
	}

	static private String[] getQualifiedNameComponents(Entity entity) {
		String ret[] = new String[5];

		if (StringUtils.equals(entity.getTypeName(), ENTITY_TYPE_HIVE_DB)) {
			ret[1] = getAttribute(entity.getValues(), "clusterName", String.class);
			ret[2] = getAttribute(entity.getValues(), "name", String.class);
			ret[3] = null;
			ret[0] = ret[1] + "." + ret[2];
		} else {
			String qualifiedName = getAttribute(entity.getValues(), ENTITY_ATTRIBUTE_QUALIFIED_NAME, String.class);

			String nameHierarchy[] = qualifiedName.split(QUALIFIED_NAME_FORMAT_DELIMITER_STRING);

			int hierarchyLevels = nameHierarchy.length;

			if (LOG.isDebugEnabled()) {
				LOG.debug("----- Entity-Id:" + entity.getId().getGuid());
				LOG.debug("----- Entity-Type-Name:" + entity.getTypeName());
				LOG.debug("----- Entity-Qualified-Name:" + qualifiedName);
				LOG.debug("-----	Entity-Qualified-Name-Components -----");
				for (int i = 0; i < hierarchyLevels; i++) {
					LOG.debug("-----		Index:" + i + "	Value:" + nameHierarchy[i]);
				}
			}

			int i;
			for (i = 0; i < ret.length; i++) {
				ret[i] = null;
			}
			ret[0] = qualifiedName;

			for (i = 0; i < hierarchyLevels; i++) {
				ret[i + 1] = nameHierarchy[i];
			}
		}
		return ret;
	}

	static private String getServiceName(String instanceName, String entityTypeName) {
		// Parse entityTypeName to get the Apache-component Name
		String apacheComponents[] = entityTypeName.split("_");
		String apacheComponent = null;
		if (apacheComponents.length > 0) {
			apacheComponent = apacheComponents[0].toLowerCase();
		}

		return TagSyncConfig.getServiceName(apacheComponent, instanceName, properties);
	}

	static private <T> T getAttribute(Map<String, Object> map, String name, Class<T> type) {
		return type.cast(map.get(name));
	}

	// Temporary stuff, until qualifiedName is implemented by Atlas
	static private String[] getTempNameComponents(Entity entity) {
		String ret[] = new String[4];
		if (StringUtils.equals(entity.getTypeName(), ENTITY_TYPE_HIVE_DB)) {
			ret[1] = getAttribute(entity.getValues(), "clusterName", String.class);
			ret[2] = getAttribute(entity.getValues(), "name", String.class);
			ret[3] = null;
			ret[0] = ret[1] + "." + ret[2];
		} else if (StringUtils.equals(entity.getTypeName(), ENTITY_TYPE_HIVE_TABLE)) {
			String qualifiedName = getAttribute(entity.getValues(), "name", String.class);
			String nameHierarchy[] = qualifiedName.split("\\.@");

			int hierarchyLevels = nameHierarchy.length;

			if (LOG.isDebugEnabled()) {
				LOG.debug("----- Entity-Id:" + entity.getId().getGuid());
				LOG.debug("----- Entity-Type-Name:" + entity.getTypeName());
				LOG.debug("----- Entity-Qualified-Name:" + qualifiedName);
				LOG.debug("-----	Entity-Qualified-Name-Components -----");
				for (int i = 0; i < hierarchyLevels; i++) {
					LOG.debug("-----		Index:" + i + "	Value:" + nameHierarchy[i]);
				}
			}

			int i;
			for (i = 0; i < ret.length; i++) {
				ret[i] = null;
			}
			ret[0] = qualifiedName;
			if (hierarchyLevels > 2) {
				ret[1] = nameHierarchy[2];
			}
			if (hierarchyLevels > 1) {
				ret[2] = nameHierarchy[1];
			}
			if (hierarchyLevels > 0) {
				ret[3] = nameHierarchy[0];
			}


		}
		return ret;
	}


	static private ServiceTags handleEntityUpdate(Entity entity) throws Exception {

		throw new Exception("Not implemented");

	}

	static private ServiceTags handleTraitDelete(Entity entity) throws Exception {

		throw new Exception("Not implemented");
	}
}