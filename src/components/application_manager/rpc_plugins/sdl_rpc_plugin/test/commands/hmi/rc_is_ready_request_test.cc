/*
 * Copyright (c) 2018, Ford Motor Company
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the
 * distribution.
 *
 * Neither the name of the Ford Motor Company nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

#include "hmi/rc_is_ready_request.h"

#include "gtest/gtest.h"

#include "application_manager/commands/command_request_test.h"
#include "application_manager/event_engine/event.h"
#include "application_manager/hmi_interfaces.h"
#include "application_manager/mock_application_manager.h"
#include "application_manager/mock_hmi_capabilities.h"
#include "application_manager/mock_hmi_interface.h"
#include "application_manager/mock_message_helper.h"
#include "application_manager/smart_object_keys.h"
#include "smart_objects/smart_object.h"

namespace test {
namespace components {
namespace commands_test {
namespace hmi_commands_test {
namespace rc_is_ready_request {

using ::testing::_;
using ::testing::ReturnRef;
namespace am = ::application_manager;
using am::commands::MessageSharedPtr;
using am::event_engine::Event;
using sdl_rpc_plugin::commands::RCIsReadyRequest;

typedef std::shared_ptr<RCIsReadyRequest> RCIsReadyRequestPtr;

class RCIsReadyRequestTest
    : public CommandRequestTest<CommandsTestMocks::kIsNice> {
 public:
  RCIsReadyRequestTest() : command_(CreateCommand<RCIsReadyRequest>()) {}

  void SetUpExpectations(bool is_rc_cooperating_available,
                         bool is_send_message_to_hmi,
                         bool is_message_contains_param,
                         am::HmiInterfaces::InterfaceState state) {
    if (is_send_message_to_hmi) {
      ExpectSendMessagesToHMI();
    }
    EXPECT_CALL(mock_hmi_capabilities_,
                set_is_rc_cooperating(is_rc_cooperating_available));
    if (!is_rc_cooperating_available) {
      EXPECT_CALL(mock_hmi_capabilities_, set_rc_supported(false));
    }

    if (is_message_contains_param) {
      EXPECT_CALL(app_mngr_, hmi_interfaces())
          .WillRepeatedly(ReturnRef(mock_hmi_interfaces_));
      EXPECT_CALL(
          mock_hmi_interfaces_,
          SetInterfaceState(am::HmiInterfaces::HMI_INTERFACE_RC, state));
    } else {
      EXPECT_CALL(app_mngr_, hmi_interfaces())
          .WillOnce(ReturnRef(mock_hmi_interfaces_));
      EXPECT_CALL(mock_hmi_interfaces_, SetInterfaceState(_, _)).Times(0);
    }
    EXPECT_CALL(mock_hmi_interfaces_,
                GetInterfaceState(am::HmiInterfaces::HMI_INTERFACE_RC))
        .WillOnce(Return(state));
  }

  void ExpectSendMessagesToHMI() {
    smart_objects::SmartObjectSPtr capabilities(
        new smart_objects::SmartObject(smart_objects::SmartType_Map));
    EXPECT_CALL(mock_message_helper_,
                CreateModuleInfoSO(hmi_apis::FunctionID::RC_GetCapabilities, _))
        .WillOnce(Return(capabilities));
    EXPECT_CALL(mock_rpc_service_, ManageHMICommand(capabilities, _));
  }

  void PrepareEvent(bool is_message_contain_param,
                    Event& event,
                    bool is_rc_cooperating_available = false) {
    MessageSharedPtr msg = CreateMessage(smart_objects::SmartType_Map);
    if (is_message_contain_param) {
      (*msg)[am::strings::msg_params][am::strings::available] =
          is_rc_cooperating_available;
    }
    event.set_smart_object(*msg);
  }

  void InterfacesUpdateExpectations(
      const std::set<hmi_apis::FunctionID::eType>& interfaces_to_update) {
    EXPECT_CALL(mock_hmi_capabilities_, GetDefaultInitializedCapabilities())
        .WillRepeatedly(Return(interfaces_to_update));
  }

  RCIsReadyRequestPtr command_;
};

MATCHER_P(HMIFunctionIDIs, function_id, "") {
  const auto msg_function_id = static_cast<hmi_apis::FunctionID::eType>(
      (*arg)[am::strings::params][am::strings::function_id].asInt());

  return msg_function_id == function_id;
}

TEST_F(RCIsReadyRequestTest,
       OnEvent_NoKeyAvailableInMessage_HmiInterfacesIgnored_CacheIsAbsent) {
  const bool is_rc_cooperating_available = false;
  const bool is_send_message_to_hmi = true;
  const bool is_message_contain_param = false;
  Event event(hmi_apis::FunctionID::RC_IsReady);
  PrepareEvent(is_message_contain_param, event);
  std::set<hmi_apis::FunctionID::eType> interfaces_to_update{
      hmi_apis::FunctionID::RC_GetCapabilities};
  InterfacesUpdateExpectations(interfaces_to_update);
  SetUpExpectations(is_rc_cooperating_available,
                    is_send_message_to_hmi,
                    is_message_contain_param,
                    am::HmiInterfaces::STATE_NOT_RESPONSE);

  ASSERT_TRUE(command_->Init());
  command_->Run();
  command_->on_event(event);
}

TEST_F(RCIsReadyRequestTest,
       OnEvent_KeyAvailableEqualToFalse_StateNotAvailable_CacheIsAbsent) {
  const bool is_rc_cooperating_available = false;
  const bool is_send_message_to_hmi = false;
  const bool is_message_contain_param = true;
  Event event(hmi_apis::FunctionID::RC_IsReady);
  PrepareEvent(is_message_contain_param, event);
  SetUpExpectations(is_rc_cooperating_available,
                    is_send_message_to_hmi,
                    is_message_contain_param,
                    am::HmiInterfaces::STATE_NOT_AVAILABLE);

  ASSERT_TRUE(command_->Init());
  command_->Run();
  command_->on_event(event);
}

TEST_F(RCIsReadyRequestTest,
       OnEvent_KeyAvailableEqualToTrue_StateAvailable_CacheIsAbsent) {
  const bool is_rc_cooperating_available = true;
  const bool is_send_message_to_hmi = true;
  const bool is_message_contain_param = true;
  Event event(hmi_apis::FunctionID::RC_IsReady);
  PrepareEvent(is_message_contain_param, event, is_rc_cooperating_available);
  std::set<hmi_apis::FunctionID::eType> interfaces_to_update{
      hmi_apis::FunctionID::RC_GetCapabilities};
  InterfacesUpdateExpectations(interfaces_to_update);
  SetUpExpectations(is_rc_cooperating_available,
                    is_send_message_to_hmi,
                    is_message_contain_param,
                    am::HmiInterfaces::STATE_AVAILABLE);

  ASSERT_TRUE(command_->Init());
  command_->Run();
  command_->on_event(event);
}

TEST_F(RCIsReadyRequestTest,
       OnEvent_HMIDoestRespond_SendMessageToHMIByTimeout_CacheIsAbsent) {
  std::set<hmi_apis::FunctionID::eType> interfaces_to_update{
      hmi_apis::FunctionID::RC_GetCapabilities};
  InterfacesUpdateExpectations(interfaces_to_update);
  ExpectSendMessagesToHMI();

  ASSERT_TRUE(command_->Init());
  command_->Run();
  command_->onTimeOut();
}

TEST_F(
    RCIsReadyRequestTest,
    OnEvent_RCGetCapabilitiesExistInTheCache_DoesntSendRCGetCapabilitiesRequest) {
  const bool is_message_contain_param = true;
  Event event(hmi_apis::FunctionID::RC_IsReady);
  PrepareEvent(is_message_contain_param, event);
  std::set<hmi_apis::FunctionID::eType> interfaces_to_update;
  InterfacesUpdateExpectations(interfaces_to_update);

  EXPECT_CALL(mock_rpc_service_,
              ManageHMICommand(
                  HMIFunctionIDIs(hmi_apis::FunctionID::RC_GetCapabilities), _))
      .Times(0);

  ASSERT_TRUE(command_->Init());
  command_->Run();
  command_->on_event(event);
}

}  // namespace rc_is_ready_request
}  // namespace hmi_commands_test
}  // namespace commands_test
}  // namespace components
}  // namespace test
