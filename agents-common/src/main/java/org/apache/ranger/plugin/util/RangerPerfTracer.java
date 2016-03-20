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

package org.apache.ranger.plugin.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.lang.StringUtils;

public class RangerPerfTracer {
<<<<<<< HEAD
	private final Log    logger;
	private final String tag;
	private final long   startTimeMs;

	public static Log getPerfLogger(String name) {
		return LogFactory.getLog("ranger.perf." + name);
=======
	protected final Log    logger;
	protected final String tag;
	protected final String data;
	private final long   startTimeMs;

	private static long reportingThresholdMs = 0L;

	private final static String tagEndMarker = "(";

	public static Log getPerfLogger(String name) {
		return LogFactory.getLog("org.apache.ranger.perf." + name);
>>>>>>> refs/remotes/apache/master
	}

	public static Log getPerfLogger(Class<?> cls) {
		return RangerPerfTracer.getPerfLogger(cls.getName());
	}

	public static boolean isPerfTraceEnabled(Log logger) {
<<<<<<< HEAD
		return logger.isInfoEnabled();
	}

	public static RangerPerfTracer getPerfTracer(Log logger, String tag) {
		return logger.isInfoEnabled() ? new RangerPerfTracer(logger, tag) : null;
	}

	public static RangerPerfTracer getPerfTracer(Log logger, Object... tagParts) {
		return logger.isInfoEnabled() ? new RangerPerfTracer(logger, StringUtils.join(tagParts)) : null;
=======
		return logger.isDebugEnabled();
	}

	public static RangerPerfTracer getPerfTracer(Log logger, String tag) {
		String data = "";
		String realTag = "";

		if (tag != null) {
			int indexOfTagEndMarker = StringUtils.indexOf(tag, tagEndMarker);
			if (indexOfTagEndMarker != -1) {
				realTag = StringUtils.substring(tag, 0, indexOfTagEndMarker);
				data = StringUtils.substring(tag, indexOfTagEndMarker);
			} else {
				realTag = tag;
			}
		}
		return RangerPerfTracerFactory.getPerfTracer(logger, realTag, data);
	}

	public static RangerPerfTracer getPerfTracer(Log logger, String tag, String data) {
		return RangerPerfTracerFactory.getPerfTracer(logger, tag, data);
>>>>>>> refs/remotes/apache/master
	}

	public static void log(RangerPerfTracer tracer) {
		if(tracer != null) {
			tracer.log();
		}
	}

<<<<<<< HEAD
	public RangerPerfTracer(Log logger, String tag) {
		this.logger = logger;
		this.tag    = tag;
=======
	public RangerPerfTracer(Log logger, String tag, String data) {
		this.logger = logger;
		this.tag    = tag;
		this.data	= data;
>>>>>>> refs/remotes/apache/master
		startTimeMs = System.currentTimeMillis();
	}

	public final String getTag() {
		return tag;
	}

	public final long getStartTime() {
		return startTimeMs;
	}

	public final long getElapsedTime() {
		return System.currentTimeMillis() - startTimeMs;
	}

	public void log() {
<<<<<<< HEAD
		if(logger.isInfoEnabled()) {
			logger.info("[PERF] " + tag + ": " + getElapsedTime());
=======
		long elapsedTime = getElapsedTime();
		if (elapsedTime > reportingThresholdMs) {
			logger.debug("[PERF] " + tag + data + ": " + elapsedTime);
>>>>>>> refs/remotes/apache/master
		}
	}
}
