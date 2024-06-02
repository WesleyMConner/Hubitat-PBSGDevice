// ---------------------------------------------------------------------------------
// P B S G 2
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
  // Once one or more PBSG instances are created, this method is called
  // to communicate a PBSG instance state change. This method is typically
  // developed to process state changes based on the instance type and/or
  // the instance name.
  logInfo('pbsg_ButtonOnCallback', pbsg_State(pbsg))
}

ArrayList getTestActions(String pbsgName) {
  // Develop Available Test Sequence Options
  ArrayList buttons = buttonStringToButtonList("${pbsgName}_allSettingsKey")
  ArrayList testActions = new ArrayList()
  buttons.each{ b ->
    testActions << "${b}_On"
    testActions << "${b}_Off"
  }
  testActions.add('Activate last active')
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
    width: 5,
    options: getTestActions(pbsgName)
  )
  settings."${testSeqKey}" = testSeqJson
  paragraph('>>', width: 2)
  app.removeSetting(testSeqKey)
  input(
    name: testSeqKey,
    title: "Save ${b(pbsgName)} Test Sequence",
    type: 'textarea',
    rows: testSeqList?.size(),
    submitOnChange: true,
    required: true,
    width: 5,
    defaultValue: testSeqJson
  )
  paragraph(
    "Once 'Done' is pressed, the Test Sequence results should appear in the log."
  )
}

preferences {
  page(name: 'demoPbsg', nextPage: 'testActionsPage')
  page(name: 'testActionsPage', nextPage: 'testSequencePage')
  page(name: 'testSequencePage')
}

String getPbsgNames () {
  return settings.pbsgNames?.tokenize(' ')
}

Map demoPbsg() {
  // Solicit 'settings' for each PBSG Name from the client.
  return dynamicPage(
    name: 'demoPbsg',
    title: h1("TestPBSG (${app.id})"),
    uninstall: true
  ) {
    atomicState.remove('null')
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
  pbsgNames.each { pbsgName ->
    Map pbsgConfig = createPbsgStateFromConfig(pbsgName)
    Map pbsg = pbsg_BuildToConfig(pbsgConfig)
    String testSeqKey = "${pbsgName}_TestSequence"
    String testSeqJson = settings."${testSeqKey}"
    ArrayList testSeqList = fromJson(testSeqJson)
    Integer actionCnt = testSeqList.size()
    testSeqList?.eachWithIndex{ testAction, index ->
      logInfo('initialize', "Taking Action ${index + 1} of ${actionCnt}: ${b(testAction)}")
      if (testAction == 'Activate last active') {
        pbsg_ActivateLastActive(pbsg) && updatePbsgState(pbsg)
      } else {
        ArrayList tokenizedAction = testAction.tokenize('_')
        if (tokenizedAction.size() == 2) {
          String button = tokenizedAction[0]
          String action = tokenizedAction[1]
          //logInfo('initialize#258', "${pbsg_State(pbsg)} -> ${button} : ${action}")
          switch (action) {
            case 'On':
              pbsg_ActivateButton(pbsg, button) && updatePbsgState(pbsg)
              //-> logInfo('initialize', pbsg_State(pbsg))
              break
            case 'Off':
              pbsg_DeactivateButton(pbsg, button) && updatePbsgState(pbsg)
              //-> logInfo('initialize', pbsg_State(pbsg))
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
}
