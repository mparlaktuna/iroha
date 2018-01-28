/**
 * Copyright Soramitsu Co., Ltd. 2017 All Rights Reserved.
 * http://soramitsu.co.jp
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <grpc++/grpc++.h>
#include <grpc++/server_builder.h>
#include "torii/command_service.hpp"
#include "torii/torii_service_handler.hpp"

#ifndef MAIN_SERVER_RUNNER_HPP
#define MAIN_SERVER_RUNNER_HPP

namespace torii {
  class ToriiServiceHandler;
}

/**
 * Class runs Torii server for handling queries and commands.
 */
class ServerRunner {
 public:
  /**
   * Constructor. Initialize a new instance of ServerRunner class.
   * @param address - the address the server will be bind to in URI form
   */
  explicit ServerRunner(const std::string &address);

  /**
   * Destructor. Shutdown the server.
   */
  ~ServerRunner();

  /**
   * Initialize the server and run main loop.
   * @param commandService - service for command handler
   * @param queryService - service for query handler
   */
  void run(std::unique_ptr<torii::CommandService> commandService,
           std::unique_ptr<torii::QueryService> queryService);

  /**
   * Release the completion queues and shutdown the server.
   */
  void shutdown();

  /**
   * Wait until the server is up.
   */
  void waitForServersReady();

 private:
  std::unique_ptr<grpc::Server> serverInstance_;
  std::mutex waitForServer_;
  std::condition_variable serverInstanceCV_;

  std::string serverAddress_;
  std::unique_ptr<torii::ToriiServiceHandler> toriiServiceHandler_;
};

#endif  // MAIN_SERVER_RUNNER_HPP
