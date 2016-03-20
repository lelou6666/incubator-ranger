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

package org.apache.ranger.tagsync.source.atlasrest;

import com.google.gson.Gson;

import com.google.gson.GsonBuilder;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.ranger.tagsync.model.AbstractTagSource;
import org.apache.ranger.plugin.util.ServiceTags;
import org.apache.ranger.tagsync.model.TagSink;
import org.apache.ranger.tagsync.process.TagSyncConfig;
import org.apache.ranger.tagsync.process.TagSynchronizer;
import org.apache.ranger.tagsync.source.atlas.AtlasEntityWithTraits;
import org.apache.ranger.tagsync.source.atlas.AtlasNotificationMapper;
import org.apache.ranger.tagsync.source.atlas.AtlasResourceMapperUtil;

import java.util.*;

public class AtlasRESTTagSource extends AbstractTagSource implements Runnable {
	private static final Log LOG = LogFactory.getLog(AtlasRESTTagSource.class);

	private String atlasEndpoint;
	private long sleepTimeBetweenCycleInMillis;

	private Thread myThread = null;

	public static void main(String[] args) {

		AtlasRESTTagSource atlasRESTTagSource = new AtlasRESTTagSource();

		TagSyncConfig config = TagSyncConfig.getInstance();

		Properties props = config.getProperties();

		TagSynchronizer.printConfigurationProperties(props);

		TagSink tagSink = TagSynchronizer.initializeTagSink(props);

		if (tagSink != null) {

			if (atlasRESTTagSource.initialize(props)) {
				try {
					tagSink.start();
					atlasRESTTagSource.setTagSink(tagSink);
					atlasRESTTagSource.synchUp();
				} catch (Exception exception) {
					LOG.error("ServiceTags upload failed : ", exception);
					System.exit(1);
				}
			} else {
				LOG.error("AtlasRESTTagSource initialized failed, exiting.");
				System.exit(1);
			}

		} else {
			LOG.error("TagSink initialialization failed, exiting.");
			System.exit(1);
		}

	}
	@Override
	public boolean initialize(Properties properties) {
		if (LOG.isDebugEnabled()) {
			LOG.debug("==> AtlasRESTTagSource.initialize()");
		}

		boolean ret = AtlasResourceMapperUtil.initializeAtlasResourceMappers(properties);

		atlasEndpoint = TagSyncConfig.getAtlasEndpoint(properties);
		sleepTimeBetweenCycleInMillis = TagSyncConfig.getTagSourceAtlasDownloadIntervalInMillis(properties);

		if (StringUtils.isEmpty(atlasEndpoint)) {
			LOG.info("No AtlasEndpoint specified, Initial download of Atlas-entities cannot be done.");
			ret = false;
		}

		if (LOG.isDebugEnabled()) {
			LOG.debug("<== AtlasRESTTagSource.initialize(), result=" + ret);
		}

		return ret;
	}

	@Override
	public boolean start() {

		myThread = new Thread(this);
		myThread.setDaemon(true);
		myThread.start();

		return true;
	}

	@Override
	public void stop() {
		if (myThread != null && myThread.isAlive()) {
			myThread.interrupt();
		}
	}

	@Override
	public void run() {

		if (LOG.isDebugEnabled()) {
			LOG.debug("==> AtlasRESTTagSource.run()");
		}

		while (true) {

			synchUp();

			LOG.debug("Sleeping for [" + sleepTimeBetweenCycleInMillis + "] milliSeconds");

			try {

				Thread.sleep(sleepTimeBetweenCycleInMillis);

			} catch (InterruptedException exception) {
				LOG.error("Interrupted..: ", exception);
				return;
			}
		}
	}

	public void synchUp() {

		AtlasRESTUtil atlasRESTUtil = new AtlasRESTUtil(atlasEndpoint);

		List<AtlasEntityWithTraits> atlasEntitiesWithTraits = atlasRESTUtil.getEntitiesWithTraits();

		if (CollectionUtils.isNotEmpty(atlasEntitiesWithTraits)) {
			if (LOG.isDebugEnabled()) {
				for (AtlasEntityWithTraits element : atlasEntitiesWithTraits) {
					LOG.debug(element);
				}
			}

			Map<String, ServiceTags> serviceTagsMap = AtlasNotificationMapper.processEntitiesWithTraits(atlasEntitiesWithTraits);

			if (MapUtils.isNotEmpty(serviceTagsMap)) {
				for (Map.Entry<String, ServiceTags> entry : serviceTagsMap.entrySet()) {
					if (LOG.isDebugEnabled()) {
						Gson gsonBuilder = new GsonBuilder().setDateFormat("yyyyMMdd-HH:mm:ss.SSS-Z")
								.setPrettyPrinting()
								.create();
						String serviceTagsString = gsonBuilder.toJson(entry.getValue());

						LOG.debug("serviceTags=" + serviceTagsString);
					}
					updateSink(entry.getValue());
				}
			}
		}

	}

}

