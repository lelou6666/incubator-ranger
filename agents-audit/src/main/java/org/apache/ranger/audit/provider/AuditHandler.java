/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ranger.audit.provider;

<<<<<<< HEAD:agents-audit/src/main/java/com/xasecure/audit/provider/AuditProvider.java
import java.util.Properties;

import com.xasecure.audit.model.AuditEventBase;
=======
import java.util.Collection;
import java.util.Properties;
>>>>>>> refs/remotes/apache/master:agents-audit/src/main/java/org/apache/ranger/audit/provider/AuditHandler.java

import org.apache.ranger.audit.model.AuditEventBase;

<<<<<<< HEAD:agents-audit/src/main/java/com/xasecure/audit/provider/AuditProvider.java
    public void init(Properties prop);
=======
public interface AuditHandler {
	public boolean log(AuditEventBase event);
	public boolean log(Collection<AuditEventBase> events);	

	public boolean logJSON(String event);
	public boolean logJSON(Collection<String> events);	

    public void init(Properties prop);
    public void init(Properties prop, String basePropertyName);
>>>>>>> refs/remotes/apache/master:agents-audit/src/main/java/org/apache/ranger/audit/provider/AuditHandler.java
    public void start();
    public void stop();
    public void waitToComplete();
    public void waitToComplete(long timeout);

    /**
     * Name for this provider. Used only during logging. Uniqueness is not guaranteed
     */
    public String getName();

    public void    flush();
}
