/*
 * Copyright (C) 2012-2013 University of Washington
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

package org.opendatakit.aggregate.odktables.rest.serialization;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.convert.Registry;
import org.simpleframework.xml.convert.RegistryStrategy;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.strategy.Strategy;

public class SimpleXMLSerializerForAggregate {
  public static Serializer getSerializer() {
    Registry registry = new Registry();
    Strategy strategy = new RegistryStrategy(registry);
    Serializer serializer = new Persister(strategy);
    XMLListConverter converter = new XMLListConverter(serializer);
    try {
      registry.bind(List.class, converter);
      registry.bind(ArrayList.class, converter);
      registry.bind(LinkedList.class, converter);
    } catch (Exception e) {
      throw new RuntimeException("Failed to register list converters!", e);
    }
    return serializer;
  }
}
