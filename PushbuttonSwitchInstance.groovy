// ---------------------------------------------------------------------------------
// P U S H B U T T O N   S W I T C H   I N S T A N C E
//
//  Copyright (C) 2023-Present Wesley M. Conner
//
// Licensed under the Apache License, Version 2.0 (aka Apache-2.0, the
// "License"); you may not use this file except in compliance with the
// License. You may obtain a copy of the License at
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// ---------------------------------------------------------------------------------
import com.hubitat.app.DeviceWrapper as DevW
import com.hubitat.hub.domain.Event as Event
#include wesmc.UtilsLibrary

definition(
  parent: "wesmc:Pushbutton Switches",
  name: 'Pushbutton Switch Instance',
  namespace: 'wesmc',
  author: 'WesleyMConner',
  description: 'Make a group of Switches mutually-exclusive.',
  category: '',           // Not supported as of Q3'23
  iconUrl: '',            // Not supported as of Q3'23
  iconX2Url: '',          // Not supported as of Q3'23
  iconX3Url: '',          // Not supported as of Q3'23
  installOnOpen: false,
  documentationLink: '',  // TBD
  videoLink: '',          // TBD
  importUrl: '',          // TBD
  oauth: false,           // Even if used, must be manually enabled.
  singleInstance: false
)

// -------------------------------
// C L I E N T   I N T E R F A C E
// -------------------------------
preferences {
  page(name: 'monoPage', uninstall: true, install: true)
}

Map monoPage() {
  return dynamicPage(name: 'monoPage') {
    // A single section block minimizes whitespace.
    section {
      paragraph(
        heading('Pushbutton Instance')
      )
      input (
        name: 'swGroupName',
        defaultValue: 'Pushbutton Group - ',
        type: 'text',
        title: emphasis('Name this <b>Pushbutton Switch</b> group'),
        submitOnChange: true,
        required: true
      )
      if(settings.swGroupName) {
        input (
          name: 'LOG',
          type: 'bool',
          title: '<b>Enable logging?</b>',
          defaultValue: true
        )
        app.updateLabel(settings.swGroupName)
        input (
          name: 'swGroup',
          type: 'capability.switch',
          title: 'Select the <b>Participating Switches</b>',
          submitOnChange: true,
          required: true,
          multiple: true
        )
      }
      if (settings.swGroup) {
        input (
          name: 'useDefault',
          type: 'bool',
          title: 'Enable a <b>Default Switch</b>?',
          submitOnChange: true,
          required: true
        )
      }
      if (settings.useDefault) {
        List<Map<String, String>> picklist = settings.swGroup
          .sort({ it.displayName })
          .collect({ [(it.id): it.displayName] })
        input (
          name: 'defaultSwitchId',
          type: 'enum',
          title: 'Select the <b>Default Switch</b>',
          submitOnChange: false,
          required: true,
          options: picklist
        )
      } else {
        // The client MAY select and subsequently de-select 'useDefault'.
        // Remove any prior 'defaultSwitchId' entry for consistency.
        app.removeSetting('defaultSwitchId')
      }
    }
  }
}

// ---------------------------------------------------
// I N I T I A L I Z A T I O N   &   O P E R A T I O N
// ---------------------------------------------------
void installed() {
  if (settings.LOG) log.trace 'installed()'
  initialize()
}

void updated() {
  if (settings.LOG) log.trace 'updated()'
  unsubscribe()  // Suspend event processing to rebuild state variables.
  unschedule()   // Placeholder for any future scheduled jobs.
  initialize()
}

String extractSwitchState(DevW d) {
  // What's best here? NOT exhaustively tested.
  //   - stateValues = d.collect({ it.currentStates.value }).flatten()
  //   - stateValues = d.currentStates.value
  List<String> stateValues = d.collect({ it.currentStates.value }).flatten()
  return stateValues.contains('on')
      ? 'on'
      : stateValues.contains('off')
        ? 'off'
        : 'unknown'
}

String showSwitchInfoWithState(
  String delimiter = ', ',
  List<DevW> devices = null  // settings.swGroup is only available in fn body ?!
) {
  if (!devices) devices = settings.swGroup
  return devices.collect({
    "${deviceTag(it)} ${emphasisOn(extractSwitchState(it))}"
  }).sort().join(delimiter) ?: 'N/A'
}

DevW getSwitchById(String id) {
  List<DevW> devices = settings.swGroup
  return devices?.find({ it.id == id })
}

void logSettingsAndState(String calledBy) {
  if (settings.LOG) log.trace """logSettingsAndState() from ${calledBy}:<br/>
    <table>
      <tr>
        <th align='right'>LOG:</th>
        <td>${settings.LOG}</td>
      </tr>
      <tr>
        <th align='right'>swGroupName:</th>
        <td>${settings.swGroupName}</td>
      </tr>
      <tr>
        <th align='right'>Switch State:</th>
        <td>${showSwitchInfoWithState(', ')}</td>
      </tr>
      <tr>
        <th align='right'>Default Switch:</th>
        <td>${settings.useDefault
                ? deviceTag(getSwitchById(settings.defaultSwitchId))
                : 'N/A'
            }
        </td>
      </tr>
    </table>
  """
}

List<DevW> getOnSwitches() {
  List<DevW> devices = settings.swGroup
  return devices?.findAll({ extractSwitchState(it) == 'on' })
}

void enforceDefault() {
  if (settings.LOG) log.trace 'enforceDefault()'
  if (settings.defaultSwitchId) {
    List<DevW> onDevices = getOnSwitches()
    if (onDevices.size() == 0) {
      logSettingsAndState('enforceDefault() triggered IN')
      DevW defaultSwitch = getSwitchById(settings.defaultSwitchId)
      if (settings.LOG) log.trace "enforceDefault() turning on ${deviceTag(defaultSwitch)}."
      defaultSwitch.on()
      logSettingsAndState('enforceDefault() triggered OUT')
    }
  }
}

void enforceMutualExclusion() {
  if (settings.LOG) log.trace 'enforceMutualExclusion()'
  List<DevW> onList = getOnSwitches()
  while (onList.size() > 1) {
    DevW device = onList.first()
    if (settings.LOG) log.trace "enforceMutualExclusion() turning off ${deviceTag(device)}."
    device.off()
    onList = onList.drop(1)
  }
}

void buttonHandler (Event e) {
  if (e.isStateChange) {
    logSettingsAndState('buttonHandler()')
    DevW eventDevice = getSwitchById(e.deviceId.toString())
    switch(e.value) {
      case 'on':
        // Turn off peers in switch group.
        getOnSwitches().each({ sw ->
          if (sw.id != eventDevice.id) {
            if (settings.LOG) log.trace "buttonHandler() turning off ${deviceTag(sw)}."
            sw.off()
          }
        })
        break
      case 'off':
        enforceDefault()
        break
      default:
        log.error  "buttonHandler() expected 'on' or 'off'; but, \
          received '${e.value}'."
    }
  } else {
    // Report this condition as an ERROR and explore further IF it occurs.
    logSettingsAndState('buttonHandler()', true)
  }
}

void initialize() {
  if (settings.LOG) log.trace 'initialize()'
  enforceMutualExclusion()
  enforceDefault()
  logSettingsAndState('initialize() about to enable subscription(s)')
  subscribe(settings.swGroup, "switch", buttonHandler)
}

String emphasisOn(String s) {
  return s == 'on' ? '<b>on</b>' : "<em>${s}</em>"
}

void uninstalled() {
  if (settings.LOG) log.trace 'uninstalled()'
  // Nothing to do. Subscruptions are automatically dropped.
  // This may matter if devices are captured by a switch group in the future.
}
