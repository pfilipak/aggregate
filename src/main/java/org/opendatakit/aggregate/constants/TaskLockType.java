/*
 * Copyright (C) 2010 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.opendatakit.aggregate.constants;

import org.opendatakit.common.persistence.ITaskLockType;
import org.opendatakit.common.persistence.PersistConsts;

/**
 * 
 * @author wbrunette@gmail.com
 * @author mitchellsundt@gmail.com
 * 
 */
public enum TaskLockType implements ITaskLockType {
  UPLOAD_SUBMISSION(60000),
  WORKSHEET_CREATION(120000),
  FORM_DELETION(120000),
  PURGE_OLDER_SUBMISSIONS(120000),
  STARTUP_SERIALIZATION(120000),
  CREATE_FORM(60000+2*PersistConsts.MAX_SETTLE_MILLISECONDS); // 60 second request timeout, 2x settle for replication delay
  
  private long timeout;

  private TaskLockType(long timeout) {
    this.timeout = timeout;
  }
 
  @Override
  public long getLockExpirationTimeout() {
    return timeout;
  }

  @Override
  public String getName() {
	return name();
  }
}
