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

#include "module/irohad/ametsuchi/ametsuchi_mocks.hpp"
#include "module/irohad/model/model_mocks.hpp"
#include "validation/impl/chain_validator_impl.hpp"
#include "interfaces/iroha_internal/block.hpp"
#include "builders/protobuf/builder_templates/block_template.hpp"
#include "cryptography/hash.hpp"

#include "backend/protobuf/from_old_model.hpp"

using namespace iroha;
using namespace iroha::model;
using namespace iroha::validation;
using namespace iroha::ametsuchi;

using ::testing::_;
using ::testing::A;
using ::testing::ByRef;
using ::testing::InvokeArgument;
using ::testing::Return;

class ChainValidationTest : public ::testing::Test {
 public:
  void SetUp() override {
    validator = std::make_shared<ChainValidatorImpl>();
    storage = std::make_shared<MockMutableStorage>();
    query = std::make_shared<MockWsvQuery>();

    peer.pubkey.fill(2);
    peers = std::vector<Peer>{peer};

    block.sigs.emplace_back();
    block.sigs.back().pubkey = peer.pubkey;
    block.prev_hash.fill(0);
    hash = block.prev_hash;
  }

  /**
   * Get block builder to build blocks for tests
   * @return block builder
   */
  auto getBlockBuilder() const {
    constexpr auto kTotal = (1 << 5) - 1;
    return shared_model::proto::TemplateBlockBuilder<
        kTotal,
        shared_model::validation::DefaultBlockValidator,
        shared_model::proto::Block>()
        .txNumber(0)
        .height(1)
        .prevHash(shared_model::crypto::Hash(std::string(32, '0')))
        .createdTime(iroha::time::now());
  }

  Peer peer;
  std::vector<Peer> peers;
  Block block;
  hash256_t hash;

  std::shared_ptr<ChainValidatorImpl> validator;
  std::shared_ptr<MockMutableStorage> storage;
  std::shared_ptr<MockWsvQuery> query;
};

TEST_F(ChainValidationTest, ValidCase) {
  // Valid previous hash, has supermajority, correct peers subset => valid

  EXPECT_CALL(*query, getPeers()).WillOnce(Return(peers));

  EXPECT_CALL(*storage, apply(/* block */ _, _)) // TODO replace with shared_model block
      .WillOnce(InvokeArgument<1>(ByRef(block), ByRef(*query), ByRef(hash)));

  auto new_block = getBlockBuilder().build();
//  new_block.addSignature(); // TODO add signature
  ASSERT_TRUE(validator->validateBlock(new_block, *storage));
}

TEST_F(ChainValidationTest, FailWhenDifferentPrevHash) {
  // Invalid previous hash, has supermajority, correct peers subset => invalid

  hash.fill(1);

  EXPECT_CALL(*query, getPeers()).WillOnce(Return(peers));

  EXPECT_CALL(*storage, apply(/* block */ _, _)) // TODO replace with shared_model block
      .WillOnce(InvokeArgument<1>(ByRef(block), ByRef(*query), ByRef(hash)));


  auto new_block = getBlockBuilder().build();
//  new_block.addSignature(); // TODO add signature
  ASSERT_FALSE(validator->validateBlock(new_block, *storage));
}

TEST_F(ChainValidationTest, FailWhenNoSupermajority) {
  // Valid previous hash, no supermajority, correct peers subset => invalid
  EXPECT_CALL(*query, getPeers()).WillOnce(Return(peers));

  block.sigs.clear();
  EXPECT_CALL(*storage, apply(/* block */ _, _)) // TODO replace with shared_model block
      .WillOnce(InvokeArgument<1>(ByRef(block), ByRef(*query), ByRef(hash)));

  auto new_block = getBlockBuilder().build();
  ASSERT_FALSE(validator->validateBlock(new_block, *storage));
}

TEST_F(ChainValidationTest, FailWhenBadPeer) {
  // Valid previous hash, has supermajority, incorrect peers subset => invalid

  block.sigs.back().pubkey.fill(1);

  EXPECT_CALL(*query, getPeers()).WillOnce(Return(peers));

  EXPECT_CALL(*storage, apply(/* block */ _, _)) // TODO replace with shared_model block
      .WillOnce(InvokeArgument<1>(ByRef(block), ByRef(*query), ByRef(hash)));

  auto new_block = getBlockBuilder().build();
//  new_block.addSignature(); // TODO add signature
  ASSERT_FALSE(validator->validateBlock(new_block, *storage));
}

TEST_F(ChainValidationTest, ValidWhenValidateChainFromOnePeer) {
  // Valid previous hash, has supermajority, correct peers subset => valid

  EXPECT_CALL(*query, getPeers()).WillOnce(Return(peers));

  auto block_observable = rxcpp::observable<>::just(block);

  EXPECT_CALL(*storage, apply(/* block */ _, _)) // TODO replace with shared_model block
      .WillOnce(InvokeArgument<1>(ByRef(block), ByRef(*query), ByRef(hash)));

  ASSERT_TRUE(validator->validateChain(block_observable, *storage));
}
