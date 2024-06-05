// ---------------------------------------------------------------------------------
// D E M O   P B S G
//
// Copyright (C) 2023-Present Wesley M. Conner
//
// LICENSE
// Licensed under the Apache License, Version 2.0 (aka Apache-2.0, the
// "License"), see http://www.apache.org/licenses/LICENSE-2.0. You may
// not use this file except in compliance with the License. Unless
// required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
// implied.
// ---------------------------------------------------------------------------------
// For reference:
//   Unicode 2190 ← LEFTWARDS ARROW
//   Unicode 2192 → RIGHTWARDS ARROW
import com.hubitat.hub.domain.Event as Event
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

// The Groovy Linter generates NglParseError on Hubitat #include !!!
#include wesmc.lUtils
#include wesmc.lPBSG

definition (
  name: 'Demo-PBSG',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Demonstrate PushButtonSwitchGroup (PBSG) Functionality',
  singleInstance: true,
  iconUrl: '',
  iconX2Url: ''
)

void pbsg_ButtonOnCallback(Map pbsg) {
  // Clients specialize this method to handle PBSG updates. [The callback
  // is used in lieu of sending App-to-App events.]
  //   - Clients may have more than one PBSG instance (distinct Maps in
  //     AtomicState).
  //   - Clients can also extend PBSG (effectively, create a derived class)
  //     and adjust 'instType' to differentiate their extension from the
  //     'base class' (instType == 'pbsg'_.
  logInfo('pbsg_ButtonOnCallback', pbsg_State(pbsg))
}

ArrayList getTestActions(String pbsgName) {
  // Develop Available Test Sequence Options
  ArrayList buttons = buttonStringToButtonList("${pbsgName}_allSettingsKey")
  ArrayList testActions = new ArrayList()
  buttons.each{ b ->
    testActions << "${b}_ButtonOn"
    testActions << "${b}_ButtonOff"
    testActions << "${b}_VswOn"
    testActions << "${b}_VswOff"
  }
  testActions.add('Activate_last_active')
  //testActions << 'No more actions'
  return testActions
}

String toJson (def thing) {
  def output = new JsonOutput()
  return output.toJson(thing)
}

def fromJson (String json) {
  if (!json) return null
  def slurper = new JsonSlurper()
  return slurper.parseText(json)
}

void solicitTestSequence(String pbsgName, ArrayList testOptions) {
  String testSeqKey = "${pbsgName}_TestSequence"
  String testSeqJson = settings."${testSeqKey}"
  ArrayList testSeqList = fromJson(testSeqJson) ?: []
  // Add the most recent 'next action' to the 'test sequence'.
  String nextActionKey = "${pbsgName}_NextTestAction"
  String nextAction = settings."${nextActionKey}"
  if (nextAction) {
    testSeqList.add(nextAction)
    testSeqJson = toJson(testSeqList)
    // Clear prior settings and re-solicit below.
    app.removeSetting(testSeqKey)
    app.removeSetting(nextActionKey)
  }
  input(
    name: nextActionKey,
    title: "Add a ${b(pbsgName)} Test Action:",
    type: 'enum',
    submitOnChange: true,
    required: false,                 // Ensures user makes a selection!
    width: 3,
    options: getTestActions(pbsgName)
  )
  settings."${testSeqKey}" = testSeqJson
  app.removeSetting(testSeqKey)
  input(
    name: testSeqKey,
    title: "Save ${b(pbsgName)} Test Sequence",
    type: 'textarea',
    submitOnChange: true,
    required: true,
    width: 9,
    defaultValue: testSeqJson
  )
}

preferences {
  page(name: 'demoPbsg', nextPage: 'testActionsPage')
  page(name: 'testActionsPage', nextPage: 'testSequencePage')
  page(name: 'testSequencePage')
}

String getPbsgStateNames () {
  return settings.pbsgNames?.tokenize(' ')
}

Map demoPbsg() {
  // Solicit 'settings' for each PBSG Name from the client.
  return dynamicPage(
    name: 'demoPbsg',
    title: h1("TestPBSG (${app.id})"),
    uninstall: true
  ) {
    //-> atomicState.remove('null')
    section {
      input(
        name: 'pbsgNames',
        title: [
          h3("Enter one or more PBSG Names"),
          '- Separate names with spaces',
          '- Use ⏎ to save your entry '
        ].join('<br/>'),
        type: 'text',
        submitOnChange: true,
        required: true
      )
      ArrayList pbsgNames = settings.pbsgNames?.tokenize(' ')
      pbsgNames.each{ pbsgName -> solicitPbsgConfig(pbsgName) }
    }
  }
}

Map testActionsPage() {
  return dynamicPage(
    name: 'testActionsPage',
    title: h1("TestPBSG (${app.id})"),
    uninstall: true
  ) {
    section {
      ArrayList pbsgNames = settings.pbsgNames?.tokenize(' ')
      paragraph([
        'On the next page ...',
        "  You will create a ${b('Test Sequence')} per PBSG",
        "    - A ${b('Test Sequence')} includes one or more ${b('Test Actions')}",
        "    - ${b('Test Actions')} can be repeated in a ${b('Test Sequence')}"
      ].join('<br/>'))
      pbsgNames.each{ pbsgName ->
        ArrayList testActions = getTestActions(pbsgName)
        paragraph([
          h2("Test Options for the ${b(pbsgName)}"),
          "  - ${testActions.join('<br/>  - ')}"
        ].join('<br/>'))
      }
    }
  }
}

Map testSequencePage() {
  return dynamicPage(
    name: 'testSequencePage',
    title: h1("TestPBSG (${app.id})"),
    install: true,
    uninstall: true
  ) {
    section {
      paragraph([
        h2("How to build a ${b('Test Sequence')} "),
        "  - Select a ${b('Test Action')}",
        "  - Use ⏎ to append the ${b('Test Action')} to the ${b('Test Sequence')}"
      ].join('<br/>'))
      // Develop Available Test Sequence Options
      ArrayList pbsgNames = settings.pbsgNames?.tokenize(' ')
      pbsgNames.each{ pbsgName ->
        ArrayList testActions = getTestActions(pbsgName)
        solicitTestSequence(pbsgName, testActions)
      }
      //input(
      //  name: pauseForCallback,
      //  title: [
      //    h3("${b('Wait time')} (in milliseconds) for VSW Actions"),
      //    '- When a Test Sequence turns a VSW on|off there is a delay',
      //    '  before this App receives an event reporting the change.',
      //    '- An overloaded Hubitat may require a longer delay.',
      //  ],
      //  type: number,
      //  defaultValue: 200,
      //  submitOnChange: true,
      //  required: true
      //)
      paragraph(
        "Once 'Done' is pressed, the Test Sequence results should appear in the log."
      )
    }
  }
}

void installed() {
  unsubscribe()
  initialize()
}

void updated() {
  unsubscribe()
  initialize()
}

void uninstalled() {
}

void initialize() {
  setLogLevel('INFO')
  // For each pbsgName:
  //   - Process the user-provided 'settings' into a PBSG configuration Map.
  //   - The PBSG configuration Map is preserved as a top-level Map in state
  //   - The PBSG configuration Map is subsequently expanded to become a
  //     a PBSG instance.
  //   - Top-level Maps in state are efficiently processed with the
  //     atomicState() and atomicState.updateMapValue(...).
  ArrayList pbsgNames = settings.pbsgNames?.tokenize(' ')
  // Build the PBSG and run
  pbsgNames.each { pbsgName ->
    // CREATE THE PBSG INSTANCE
    //   - Here, the PBSG configuration is solicted from user input
    //   - It is possible to provide configuration by brute force:
    //     Map config = {
    //           name: ...,            // The name of the PBSG
    //            all: ...,            // ArrayList of button names
    //       instType: ...,            // 'pbsg' in most cases
    //           dflt: ...             // The default button name
    //     }
    Map pbsgConfig = gatherPbsgStateFromConfig(pbsgName)
    Map pbsg = pbsg_BuildToConfig(pbsgConfig)
    // BUILD AND RUN ANY TEST SEQUENCES
    String testSeqKey = "${pbsgName}_TestSequence"
    String testSeqJson = settings."${testSeqKey}"
    ArrayList testSeqList = fromJson(testSeqJson)
    Integer actionCnt = testSeqList.size()
    testSeqList?.eachWithIndex{ testAction, index ->
      logInfo('initialize', "Taking Action ${index + 1} of ${actionCnt}: ${b(testAction)}")
      if (testAction == 'Activate_last_active') {
        pbsg_ActivateLastActive(pbsg) && putPbsgState(pbsg)
      } else {
        ArrayList tokenizedAction = testAction.tokenize('_')
        if (tokenizedAction.size() == 2) {
          String target = tokenizedAction[0]  // Could be a button or a VSW
          String action = tokenizedAction[1]
          //logInfo('initialize#258', "${pbsg_State(pbsg)} -> ${button} : ${action}")
          switch (action) {
            case 'ButtonOn':
              pbsg_ActivateButton(pbsg, target) && putPbsgState(pbsg)
              break
            case 'ButtonOff':
              pbsg_DeactivateButton(pbsg, target) && putPbsgState(pbsg)
              break
            case 'VswOn':
              // Simulate an external VSW on
              DevW vsw = getOrCreateVswWithToggle(pbsg.name, target)
              logInfo('initialize', "Turning ${vsw.name} on")
              vsw.on()
              //-> pauseExecution(400) // Pause for pbsg_ButtonOnCallback()
              pbsg = getPbsgState(pbsgName)  // Refresh pbsg state
              break
            case 'VswOff':
              // Simulate an external VSW off
              DevW vsw = getOrCreateVswWithToggle(pbsg.name, target)
              logInfo('initialize', "Turning VSW ${vsw.name} off")
              vsw.off()
              //-> pauseExecution(400) // Wait for callback
              pbsg = getPbsgState(pbsgName)  // Refresh pbsg state
              break
            default:
              logError('initialize', [
                'unexpected',
                "testAction >${testAction}<",
                "button >${button}<",
                "action >${action}<",
              ])
          }
        } else {
          logError('initialize', "unexpected testAction >${testAction}<")
        }
      }
    }
  }
  // LOCATE AND DELETE ANY ORPHANED CHILD DEVICES
  /*
  ArrayList childDNIs = getChildDevices().collect{d -> d.getDeviceNetworkId()}
  ArrayList expectedDNIs = []
  pbsgNames.each { pbsgName ->
    Map pbsg = getPbsgState(pbsgName)
    pbsg.all.each{ button -> expectedDNIs << "${pbsgName}_${button}" }
  }
  ArrayList orphanedDNIs = childDNIs.minus(expectedDNIs)
  logInfo('initialize', ['',
    "   ${b('childDNIs')}: ${childDNIs}",
    "${b('expectedDNIs')}: ${expectedDNIs}",
    "${b('orphanedDNIs')}: ${orphanedDNIs}"
  ])
  orphanedDNIs.each{ dni ->
    logWarn('initialize', "Deleting orphaned device w/ DNI: ${b(dni)}")
    deleteChildDevice(dni)
  }
  */
}
