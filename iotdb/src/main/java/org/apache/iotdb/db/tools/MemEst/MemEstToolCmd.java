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
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.db.tools.MemEst;

import io.airlift.airline.Command;
import io.airlift.airline.Option;
import io.airlift.airline.OptionType;
import org.apache.iotdb.db.conf.IoTDBConfig;
import org.apache.iotdb.db.conf.IoTDBConstant;
import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.apache.iotdb.db.conf.adapter.IoTDBConfigDynamicAdapter;
import org.apache.iotdb.db.exception.ConfigAdjusterException;
import org.apache.iotdb.db.metadata.MManager;

@Command(name = "calmem", description = "calculate minimum memory required for writing based on the number of storage groups and timeseries")
public class MemEstToolCmd implements Runnable {

  @Option(type = OptionType.GLOBAL, title = "storage group number", name = {"-sg",
      "--storagegroup"}, description = "Storage group number")
  private String sgNumString = "10";

  @Option(type = OptionType.GLOBAL, title = "total timeseries number", name = {"-ts",
      "--tsNum"}, description = "Total timeseries number")
  private String tsNumString = "1000";

  @Option(title = "max timeseries", name = {"-mts",
      "--mtsNum"}, description = "Maximum timeseries number among storage groups, make sure that it's smaller than total timeseries number")
  private String maxTsNumString = "0";

  @Override
  public void run() {
    IoTDBConfig config = IoTDBDescriptor.getInstance().getConfig();
    long memTableSize = config.getMemtableSizeThreshold();
    int maxMemtableNumber = config.getMaxMemtableNumber();
    long tsFileSize = config.getTsFileSizeThreshold();
    long memory = IoTDBConstant.GB;
    int sgNum = Integer.parseInt(sgNumString);
    int tsNum = Integer.parseInt(tsNumString);
    int maxTsNum = Integer.parseInt(maxTsNumString);
    while (true) {
      // init parameter
      config.setAllocateMemoryForWrite(memory);
      config.setMemtableSizeThreshold(memTableSize);
      config.setMaxMemtableNumber(maxMemtableNumber);
      config.setTsFileSizeThreshold(tsFileSize);
      IoTDBConfigDynamicAdapter.getInstance().reset();
      IoTDBConfigDynamicAdapter.getInstance().setInitialized(true);
      MManager.getInstance().clear();

      int sgCnt = 1;
      int tsCnt = 1;
      try {
        for (; sgCnt <= sgNum; sgCnt++) {
          IoTDBConfigDynamicAdapter.getInstance().addOrDeleteStorageGroup(1);
        }
        for (; tsCnt <= tsNum; tsCnt++) {
          IoTDBConfigDynamicAdapter.getInstance().addOrDeleteTimeSeries(1);
          MManager.getInstance().setMaxSeriesNumberAmongStorageGroup(
              maxTsNum == 0 ? tsCnt / sgNum + 1 : Math.min(tsCnt, maxTsNum));
        }

      } catch (ConfigAdjusterException e) {
        if(sgCnt > sgNum) {
          System.out
              .print(String.format("Memory estimation progress : %d%%\r", tsCnt * 100 / tsNum));
        }
        memory += IoTDBConstant.GB;
        continue;
      }
      break;
    }
    System.out.println(String.format("SG: %d, TS: %d, MTS: %d, Memory for writing: %dGB", sgNum,
        tsNum, maxTsNum == 0 ? tsNum / sgNum + 1 : maxTsNum, memory / IoTDBConstant.GB));
  }
}
