/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.remote;

import com.intellij.execution.process.SelfKiller;

/**
 * @author Alexander Koshevoy
 */
public abstract class RemoteProcess extends Process implements SelfKiller {
  public abstract boolean killProcessTree();

  public abstract boolean isDisconnected();

  /**
   * Makes host:localPort server which is available on local side available on remote side as localhost:remotePort.
   */
  public abstract void addRemoteTunnel(int remotePort, String host, int localPort) throws RemoteSdkException;

  /**
   * Makes host:remotePort server which is available on remote side available on local side as localhost:localPort.
   */
  public abstract void addLocalTunnel(int localPort, String host, int remotePort) throws RemoteSdkException;

}
