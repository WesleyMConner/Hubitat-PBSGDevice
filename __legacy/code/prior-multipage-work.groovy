// ---------------------------------------------------------------------------------
// I N T R O D U C T I O N
//   - See "README.adoc" for a project overview.
//   - In Hubitat applications, custom classes are NOT supported.
//       - See https://docs2.hubitat.com/developer/overview.
//       - Some Lists/Maps are declared with "def" as a result.
//   - The client-provided "settings" map includes:
//       - swGroupName (a String naming the Application instance)
//       - swGroup (a List<GenericDevice>)
//       - useDefault (a Boolean)
//       - defaultSwitchId (a device Id (String) from swGroup) (optional input)
//       - readyToInitialize (a Boolean)
//   - The applications "state" map includes:
//       - state.switches
// ---------------------------------------------------------------------------------
import com.hubitat.app.DeviceWrapper as GenericDevice
import com.hubitat.hub.domain.Event as GenericEvent



//---> input name: "logEnable", type: "bool", title: "Enable logging?"







definition(
  name: 'Pushbutton Switches',
  namespace: 'WesMC',
  author: 'Wesley M. Conner',
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
  parent: '',             // If used: "namespace:app name"
  singleInstance: false
)

// ---------------------------------------------------------------------------------
// F U N C T I O N S   O P E R A T I N G   O N   C L I E N T   I N P U T
// ---------------------------------------------------------------------------------
String ExtractSwitchState(GenericDevice d) {
  log.trace "-a-"
  // Both of these produce the same result for trivial data. The expressions are
  // note exhaustively tested.
  //   - List<String> stateValues = d.collect({ it.currentStates.value }).flatten()
  //   - List<String> stateValues = d.currentStates.value
  List<String> stateValues = d.collect({ it.currentStates.value }).flatten()
  //return stateValues.contains('on') ? 'on' : (
  //  stateValues.contains('off') ? 'off' : 'unknown'
  //)
  return stateValues.contains('on')
      ? 'on'
      : stateValues.contains('off')
        ? 'off'
        : 'unknown'
}
void PopulateSwitchMap() {
  log.trace "-b-"
  // Process user-provided settings.swGroup (List<GenericDevice>) to produce
  // state.switches a Map where:
  //   - Each key is a unique device IDs
  //   - Each value is a Map with select per-device values:
  //     'displayName', 'state', 'tag', 'onFn' and 'offFn'.
  state.switches = [:]
  settings.swGroup.each({ device ->
    def switchInfo = [:]
    switchInfo['displayName'] = device.displayName
    switchInfo['state'] = ExtractSwitchState(device)
    switchInfo['tag'] = "${device.displayName} (${device.id})"
    switchInfo['onFn'] = device.&on
    switchInfo['offFn'] = device.&off
    state.switches[device.id] = switchInfo
  })
  //-- log.trace "${swGroupName} PopulateSwitchMap(): ${showSwitchInfoWithState()}"
}

// ---------------------------------------------------------------------------------
// F U N C T I O N S   O P E R A T I N G   O N   A P P   S T A T E
// ---------------------------------------------------------------------------------
Map getOnSwitches() {
  log.trace "-c-"
  return state.switches.findAll({ it.value.state == 'on' })
}
Map getOffSwitches() {
  log.trace "-d-"
  return state.switches.findAll({ it.value.state == 'off' })
}
String showSwitchInfo(
  String delimiter = ', ',
  def map = state.switches
) {
  log.trace "-e-"
  return map.collect({ it.value.tag }).sort().join(delimiter)
}
String showSwitchInfoWithState(
  String delimiter = ', ',
  def map = state.switches
) {
  log.trace "-f-"
  return map.collect({
    log.trace "-f1-"
    "${it.value.tag} '${it.value.state}'"
  }).sort().join(delimiter)
  log.trace "-f2-"

}
void enforceMutualExclusion() {
  log.trace "-g-"
  // Ensure that only one switch is active at a time. In the absence of information
  // about which switch was the last switch turned "on", remove excess switches
  // starting with the left-most switch.
  def onMap = getOnSwitches()
  log.trace """
${swGroupName} enforceMutualExclusion() IN
  On: >${showSwitchInfo(', ', onMap)}<
  Off: >${showSwitchInfo(', ', getOffSwitches())}<
"""
  while (onMap.size() > 1) {
    Map val = onMap.take(1).entrySet().iterator().next().getValue();
    log.trace """
${swGroupName} enforceMutualExclusion(): Turning off ${val.getTag}.
"""
    val.offFn()
    onMap = onMap.drop(1)
  }
  log.trace """
${swGroupName} enforceMutualExclusion() OUT
 On: >${showSwitchInfo(', ', getOnSwitches())}<
 Off: >${showSwitchInfo(', ', getOffSwitches())}<
"""
}
void enforceDefault() {
  log.trace "-h-"
  if (defaultSwitchId) {
    def defaultSwitchInfo = ${state.switches[defaultSwitchId]}
    def onMap = getOnSwitches()
    log.trace """
${swGroupName} enforceDefault() IN
  On: >${showSwitchInfo(', ', onMap)}<
  Off: >${showSwitchInfo(', ', getOffSwitches())}<
  DefaultSwitchId: >${defaultSwitchInfo.tag}<
"""
    // If the onMap is empty, turn on the default switch (if available).
    if (onMap.size() == 0 && defaultSwitchId) {
      log.trace """
${swGroupName} enforceDefault turning on ${defaultSwitchInfo.tag}.
"""
      defaultSwitchInfo.onFn()
    }
    log.trace """
${swGroupName} enforceDefault() OUT
  On: >${showSwitchInfo(', ', getOnSwitches())}<
  Off: >${showSwitchInfo(', ', getOffSwitches())}<
  Default Switch: >${defaultSwitchInfo.tag}<
"""
  }
}

// ---------------------------------------------------------------------------------
// E N A B L E   A P P L I C A T I O N   I N S T A N C E
// ---------------------------------------------------------------------------------
void installed() {
  log.trace "-i-"
  // Triggered when (multi-page) application instance data has been supplied.
  log.trace "${swGroupName} installed(): Invoking initialize()"
  if (state.readyToInitialize) initialize()
}
void updated() {
  log.trace "-j-"
  // Triggered when (multi-page) application instance data has been changed.
  log.trace "${swGroupName} updated(): Invoking unsubscribe() & initialize()"
  if (state.readyToInitialize) {
    unsubscribe()  // Stop receving previously-solicited state change events.
    initialize()
  }
}
void initialize() {
  log.trace "-k-"
  // Enforce initial application state constraints and subscribe to events.
  enforceMutualExclusion()
  enforceDefault() 
  subscribe(state.switches.values().raw, "switch", buttonHandler)
}
void uninstalled() {
  log.trace "-l-"
  // Nothing to do. Subscruptions are automatically dropped.
  // This may matter if devices are captured by a switch group in the future.
}
void buttonHandler (GenericEvent e) {
  log.trace "-m-"
  // Process events for a switch (i.e., switched "on", switched "off") and
  // enforce mutual exclusion and Default Switch expectations.
  //   - The received events (e.g., com.hubitat.hub.domain.Event@15fdd69) are
  //     instances of "com.hubitat.hub.domain.Event".
  //   - See also 'docs/event-data.adoc'.
  if (e.isStateChange) {
    log.trace """
  ${swGroupName} buttonHandler() .. ${e.displayName} (${e.deviceId}) is
  ${e.value}
  """
    // Generate an error if switch data IS NOT in the switches group!
    dim swValue = state.switches[e.deviceId]
    log.trace """
  ${swGroupName} buttonHandler() swValue = ${swValue}
  """

    // [Tactically] Cross check that generic devices and generic events have
    // the same per-switch values (i.e., that we are intpreting fields
    // correctly)
    if (swValue.displayName != e.displayName) {
      log.error """
${swGroupName} buttonHandler() .. swValue.displayName \
(${swValue.displayName}) DOES NOT match e.displayName (${e.displayName}).
"""
    }
    if (!swValue) {
      log.error """
${swGroupName} buttonHandler() .. Cannot find device Id=${e.id} among known
 switches.
"""
    } else {
      switch(e.value) {
        case 'on':
          log.trace """
${swGroupName} buttonHandler() .. Turning on ${swValue.tag}.
"""
          switchData.onFn()
          break
        case 'off':
          log.trace """
${swGroupName} buttonHandler() .. Turning off ${swValue.tag}.
"""
          switchData.offFn()
          break
        default:
          log.error  """
${swGroupName} buttonHandler(): Switches are expected to turn 'on' or 'off',
 but ${e.value} was encountered.
"""
      }
    }
    enforceMutualExclusion()
    enforceDefault() 
  }
}

// ---------------------------------------------------------------------------------
// S T A N D A R D I Z E   P A G E   S T Y L E S
// Provide a standardize layout for page styles.
//   ([
//     PageHeading('...Text/Html...'),
//     Level1Bullet('...Text/Html...'),
//          :
//     Level1Bullet(''...Text/Html...')
//   ])
// ---------------------------------------------------------------------------------
BLUE = "rgba(51, 92, 255, 1)"
LIGHT_GREY = "rgba(217, 217, 217, 1.0)"
LEVEL1_BULLET = '&nbsp;&nbsp;&nbsp;&nbsp;&#183; '
void ParagraphBlock (List<String> parms) {
  // Gathers strings (e.g., from PageHeading(), Level1Bullet(), ...) with
  // embedded formatting that is rendered in a single Hubitat paragraph call.
  String s = parms.join(' ')
 paragraph """
<style> \
.block {
  background: rgba(217, 217, 217, 1.0);
  padding: 10px;
}
.heading {
  font-size: 1.5em;
  color: ${BLUE};
}
.bullet {
  font-size: 1em;
  rgba(20, 20, 20, 1)
}
</style>
<div class='block'>${s}</div>
"""
//<div style='background: ${LIGHTGREY}; padding: 10px'>${s}</div>
}
String PageHeading(String text) {
  return "<span class='heading'>${text}</span><br/>"
}
String Level1Bullet(String text) {
  return "<span class='bullet'>${LEVEL1_BULLET}${text}</span><br/>"
}
void InstructionalComment(String text) {
  paragraph """
<style> \
.instr-comment {
  font-size: 0.8em;
  rgba(100, 100, 100, 1)
}
</style>
<span class='instr-comment'>${text}</span><br/>
"""
}

// ---------------------------------------------------------------------------------
// C L I E N T   I N T E R F A C E
// ---------------------------------------------------------------------------------
preferences {
  // 1. Solicit the name of this application instance.
  page(name: 'NameInstancePage', nextPage: 'SelectSwitchesPage', uninstall: true)
  // 2. Solicit the list of Participating Switches, swGroup (List<GenericDevice>).
  page(name: 'SelectSwitchesPage', nextPage: 'UseDefaultPage', uninstall: true)
  // 3. Initialize state.switches from swGroup (via PopulateSwitchMap()) and
  //    solicit whether a Default Switch will be used.
  page(name: 'UseDefaultPage', nextPage: 'IdentifyDefaultPage', uninstall: true)
  // 4. Solicit the Default Page if the feature is being utilized.
  page(name: 'IdentifyDefaultPage', nextPage: 'SummaryPage', uninstall: true)
  // 5. Summarize data collection and resulting initial application instance state.
  page(name: 'SummaryPage', install: true, uninstall: true)
}
Map NameInstancePage() {
  return dynamicPage(name: 'NameInstancePage') {
    state.readyToInitialize = false
    unsubscribe()  // Stop receving any previously-solicited state change events.
    section {
      ParagraphBlock([
        PageHeading('Name this group of <b>Pushbutton Switches</b>'),
      ])
      input (
        name: 'swGroupName',
        type: 'text',
        title: "<b>Instance Name</b>",
        submitOnChange: true,
        required: true,
      )
      InstructionalComment("Use the Enter|Return key to commit the Instance Name \
and to enable the 'Next' button.")
      if(swGroupName) app.updateLabel(swGroupName)
    }
  }
}
Map SelectSwitchesPage() {
  return dynamicPage(name: 'SelectSwitchesPage') {
    section {
      ParagraphBlock([
        PageHeading('Select the Participating Switches'),
        Level1Bullet('When a <b>Participating Switch</b> is turned "on" its peer \
switches are automatically turned "off".'),
        Level1Bullet('<b>Participating Switches</b> can include multiple switch \
types - physical/virtual switches/dimmers, scene activators, ...')
      ])
      input (
        name: 'swGroup',
        type: 'capability.switch',
        title: "<b>Participating Switches</b>",
        submitOnChange: true,
        required: true,
        multiple: true
      )
    }
  }
}
Map UseDefaultPage() {
  PopulateSwitchMap()
  return dynamicPage(name: 'UseDefaultPage') {
    section {
      ParagraphBlock([
        PageHeading('Enable a Default Switch?'),
        Level1Bullet('If a <b>Default Switch</b> is included, it will be \
automatically enabled when no other switch in the group is enabled.'),
        Level1Bullet('A <b>Default Switch</b> is a convenient way to trigger \
"all off" behavior, automation routines, etc.')
      ])
      input (
        name: 'useDefault',
        type: 'bool',
        title: "<b>Include a Default?</b>",
        submitOnChange: true,
        required: true
      )
    }
  }
}
Map IdentifyDefaultPage() {
  if (settings.useDefault) {
    // A Hubitat picklist is a List<Map<KEY, VALUE>> where:
    //   - KEY is "the value the client has selected" (a device Id)
    //   - VALUE is "an option presented to the client" (a deviec description).
    List<Map<String, String>> picklist = []
    state.switches.sort({ it.value.displayName }).each({ k, v ->
      picklist += Map.of(k, v.displayName)
      log.trace "${swGroupName} IdentifyDefaultPage() k: ${k} v: ${v} picklist: ${picklist}"
    })
    log.trace "${swGroupName} IdentifyDefaultPage() state.switches (sorted) = ${state.switches}"
    log.trace "${swGroupName} IdentifyDefaultPage() picklist = ${picklist}"
    return dynamicPage(name: 'IdentifyDefaultPage') {
      section {
        ParagraphBlock([
          PageHeading('Select the <b>Default Switch</b>'),
          Level1Bullet('The default must be present among the <b>Participating \
Switches</b>'),
          Level1Bullet('If necessary, use the browser to <em>go backwards</em> and \
adjust the list of <b> Participating Switches</b>'),
        ])
        input (
          name: 'defaultSwitchId',
          type: 'enum',
          title: "<b>Select the Default Switch</b>",
          submitOnChange: false,
          required: true,
          options: picklist
        )
      }
    }
  } else {
    // The client may use the browser's back button to de-select useDefault; so,
    // make sure there's no defaultSwitchId if useDefault == False. 
    app.removeSetting('defaultSwitchId')
    return SummaryPage()
  }
}
Map SummaryPage() {
  state.readyToInitialize = true
  return dynamicPage(name: 'SummaryPage') {
    section {
      paragraph """
<b style='${BLUE}'>User Input Summary</b>
<table>
  <tr valign='top'>
    <th align='right'><b>Switch Group Name:</b></th>
    <td>${settings.swGroupName}</td>
  </tr>
  <tr valign='top'>
    <th align='right'><b>Participating Switches:</b></th>
    <td>${showSwitchInfo()}</td>
  </tr>
  <tr valign='top'>
    <th align='right'><b>Participating Switches with state:</b></th>
    <td>${showSwitchInfoWithState()}</td>
  </tr>
  <tr valign='top'>
    <th align='right'><b>Use a Default Switch?:</b></th>
    <td>${settings.useDefault ? 'Yes' : 'No'}</td>
  </tr>
  <tr valign='top'>
    <th align='right'><b>Default Switch:</b></th>
    <td>
      ${settings.defaultSwitchId
        ? state.switches[settings.defaultSwitchId].tag
        : "not applicable"}
    </td>
  </tr>
</table>
"""
    }
  }
}