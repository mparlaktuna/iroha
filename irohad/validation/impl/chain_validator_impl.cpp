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

#include "validation/impl/chain_validator_impl.hpp"

#include "ametsuchi/impl/postgres_wsv_query.hpp"
#include "consensus/consensus_common.hpp"

#include "backend/protobuf/from_old_model.hpp" // TODO remove this after relocation to shared_model

namespace iroha {
  namespace validation {
    ChainValidatorImpl::ChainValidatorImpl() {
      log_ = logger::log("ChainValidator");
    }

    bool ChainValidatorImpl::validateBlock(const shared_model::interface::Block &block,
                                           ametsuchi::MutableStorage &storage) {
      log_->info("validate block: height {}, hash {}",
                 block.height(),
                 block.hash().hex());
      auto apply_block = [](
          const auto &block, auto &queries, const auto &top_hash) {
        auto peers = queries.getPeers();
        if (not peers.has_value()) {
          return false;
        }
        bool consensusSupermajority = consensus::hasSupermajority(block.signatures().size(),
                                    peers.value().size());

        std::shared_ptr<model::Block> old_block(block.makeOldModel()); // TODO remove this after relocation to shared_model
        bool peerSubset = consensus::peersSubset(old_block->sigs, peers.value());
        bool hash = block.prevHash() == top_hash;
        return hash
            and consensusSupermajority
            and peerSubset;
      };

      // Apply to temporary storage
      return storage.apply(block, apply_block);
    }

    bool ChainValidatorImpl::validateChain(Commit blocks,
                                           ametsuchi::MutableStorage &storage) {
      log_->info("validate chain...");
      return blocks
          .all([this, &storage](auto old_block) {
            auto block = shared_model::proto::from_old(old_block);
            log_->info("Validating block: height {}, hash {}",
                       block.height(),
                       block.hash().hex());
            return this->validateBlock(block, storage);
          })
          .as_blocking()
          .first();
    }
  }  // namespace validation
}  // namespace iroha
