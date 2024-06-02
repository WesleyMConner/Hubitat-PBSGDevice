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

void pbsg_ButtonOnCallback(String pbsgName) {
  // Once one or more PBSG instances are created, this method is called
  // to communicate a PBSG instance state change. This method is typically
  // developed to process state changes based on the instance type and/or
  // the instance name.
  logInfo('pbsg_ButtonOnCallback', "pbsg: ${pbsg_State(pbsgName)}")
}

preferences {
  page(name: 'namePBSGs') //, next: 'demoPbsg')
  //page(name: 'demoPbsg', next: 'testOptions') //,
  //page(name: 'testOptions', next: ''),
  //page(name: 'testSequence')
}

Map namePBSGs() {
  return dynamicPage(
    name: 'namePBSGs',
    title: h1("Demo-PBSG (${app.id})"),
    install: true,
    uninstall: true
  ) {
    section {
      paragraph("settings: ${settings}")
      String pbsgName = settings."${pbsgName}"
      if (pbsgName) {
        paragraph("PBSG Name: ${b(pbsgName)}")
        input(
          name: pause,
          title: h3('PAUSE'),
          type: 'String',
          submitOnChange: true,
          required: true,
          multiple: false
        )
      }
      input(
        name: x,
        title: h3('Name your new PBSG:'),
        type: 'String',
        submitOnChange: true,
        required: true,
        multiple: false
      )
    }
  }
}

/*
ArrayList getSolicitedPbsgNames() {
  return ['weekDays', 'dogs']
}
*/

/*
ArrayList getTestOptions (String pbsgName) {
  // Develop Available Test Sequence Options
  ArrayList buttons = buttonStringToButtonList("${pbsgName}_buttonsSettingsKey")
  ArrayList testOptions = []
  buttons.each{ b ->
    testOptions << "${b}_On"
    testOptions << "${b}_Off"
  }
  testOptions << 'Activate last active'
  testOptions << 'No more actions'
  return testOptions
}
*/

/*
void solicitTestSequence (String pbsgName, ArrayList testOptions) {
  paragraph([
    h2("Create the Test Sequence for ${pbsgName}"),
    '  - The sequence will run after "Done" is pressed',
    '  - The Hubitat log will show the actions taken and',
    '    any received button callback events.'
  ].join('<br/>'))
  String testSequenceKey = "${pbsgName}_TestSequence"
  String nextActionKey = "${pbsgName}_NextTestAction"
  String testSequenceAsString = settings."${testSequenceKey}" ?: ''
  ArrayList testSequence = testSequenceAsString.tokenize('→')
  // Process the most recently specified next action if one exists.
  String nextAction = settings."${nextActionKey}"
  if (nextAction) {
    if (nextAction[0] == 'No more actions') {
      // The client has finished building the test sequence
      return
    }
    // Grow the test sequence
    testSequence << nextAction
    testSequenceAsString = testSequence.join('→')
    // Clear the processed next action
    //app.removeSetting(nextActionKey)
  }
  //if (testSequence.size() > 0) {
    input(
      name: testSequenceKey,
      title: [
        b('Current Test Sequence'),
        i('See testActionKey below to append actions)')
      ].join('<br/>'),
      type: 'String',
      submitOnChange: true,
      required: true,
      multiple: false,
      defaultValue: testSequenceAsString
    )
  //}
  input(
    name: testActionKey,
    title: "Next Test Action for ${b(pbsgName)}:",
    type: 'enum',
    submitOnChange: true,
    required: true,                 // Ensures user makes a selection!
    multiple: false,
    options: testOptions
  )
}
*/

Map demoPbsg() {
  // Solicit 'settings' for each PBSG Name from the client.
  return dynamicPage(
    name: 'demoPbsg',
    title: h1("Demo-PBSG (${app.id})"),
    install: true,
    uninstall: true
  ) {
    section {
      getSolicitedPbsgNames().each{ pbsgName ->
        solicitPbsgConfig(pbsgName)
      }
    }
  }
}

/*
Map testOptions() {
  return dynamicPage(
    name: 'testOptions',
    title: h1("TestPBSG (${app.id})"),
    install: true,
    uninstall: true
  ) {
    section {
      paragraph(
        h2("On the next page, you will create a test for your PBSG(s)"),
      )
      getSolicitedPbsgNames().each{ pbsgName ->
        ArrayList testOptions = getTestOptions(pbsgName)
        paragraph(
          h1("Test Options for ${pbsgName}:"),
        )
      }
      // String testSequenceAsString = settings."${testSequenceKey}" ?: ''
      // ArrayList testSequence = testSequenceAsString.tokenize('→')
    }
  // Process the most recently specified next action if one exists.
  String nextAction = settings."${nextActionKey}"
  if (nextAction) {
    if (nextAction[0] == 'No more actions') {
      // The client has finished building the test sequence
      return
    }
    // Grow the test sequence
    testSequence << nextAction
    testSequenceAsString = testSequence.join('→')
    // Clear the processed next action
    //app.removeSetting(nextActionKey)
  }
  //if (testSequence.size() > 0) {
    input(
      name: testSequenceKey,
      title: [
        b('Current Test Sequence'),
        i('See testActionKey below to append actions)')
      ].join('<br/>'),
      type: 'String',
      submitOnChange: true,
      required: true,
      multiple: false,
      defaultValue: testSequenceAsString
    )
       ArrayList testOptions = getTestOptions(pbsgName)
      }
    }
  }
}
*/

/*
Map testSequence() {
  return dynamicPage(
    name: 'testSequence',
    title: h1("TestPBSG (${app.id})"),
    install: true,
    uninstall: true
  ) {
    section {
      getSolicitedPbsgNames().each{ pbsgName ->
        solicitTestSequence(pbsgName, testOptions)
      }
    }
  }
}
*/

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
  getSolicitedPbsgNames().each { pbsgName ->
    logInfo('initialize #83', atomicState."${pbsgName}"?.toString())
    pbsg_CollectSolicitedConfig(pbsgName)
    logInfo('initialize #85', atomicState."${pbsgName}"?.toString())
    pbsg_BuildToConfig(pbsgName)
    logInfo('initialize #87', atomicState."${pbsgName}"?.toString())
  }
}

