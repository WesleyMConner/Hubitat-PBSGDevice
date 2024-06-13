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

//-> void pbsg_ButtonOnCallback(Map pbsg) {
//->   // Clients specialize this method to handle PBSG updates. [The callback
//->   // is used in lieu of sending App-to-App events.]
//->   //   - Clients may have more than one PBSG instance (distinct Maps in
//->   //     AtomicState).
//->   //   - Clients can also extend PBSG (effectively, create a derived class)
//->   //     and adjust 'instType' to differentiate their extension from the
//->   //     'base class' (instType == 'pbsg'_.
//->   logInfo('pbsg_ButtonOnCallback', pbsg_State(pbsg))
//-> }

////
//// GENERAL-PURPOSE EVENT HANDLER
////

void pbsgHandler(Event e) {
  // This very-generic handler logs events for:
  //   - Multiple PBSG Instances
  //   - Multiple PBSG Event Types
  logInfo('pbsgHandler', eventDetails(e))
}

////
//// SOLICIT DATA FOR ONE OR MORE PBSG INSTANCES
////

Boolean isDynamic() {
  return (device?.getName()) ? false : true
}

void solicitPbsgNames() {
  input(
    name: 'pbsgNames',
    title: [
      h3('Enter one or more PBSG Names'),
      '- Separate names with spaces',
      "- Avoid special characters in names ('_' is allowed)",
      '- The names will be used to create PBSG devices',
      '- Use ⏎ to save your entry '
    ].join('<br/>'),
    type: 'text',
    required: true,
    submitOnChange: true
  )
}

ArrayList getPbsgNames() {
  String namesStr = settings.pbsgNames
  // Drop non-word (\W) chars except for underscores and spaces.
  namesStr?.replaceAll(/[\W_&&[^_ ]]/, '')
  // Return one array entry for every non-null, space-delimited word.
  return namesStr?.tokenize(' ')
}

void solicitButtonNames(String prefix) {
  String header = "${b('Button Names')} (space delimited)"
  if (prefix) { header << "for ${prefix}" }
  input(
    name: "${prefix}buttons",
    title: "${h3(header)}",
    type: 'text',
    required: true,
    submitOnChange: dynamic
  )
}

ArrayList getButtonNames(String prefix) {
  // Tokenize the space-delimited buttons to produce the ArrayList
  String buttonsString = settings?."${prefix}buttons"
  // Drop non-word (\W) chars except for underscores and spaces.
  buttonsString?.replaceAll(/[\W_&&[^_ ]]/, '')
  // Return one array entry for every non-null, space-delimited word.
  return buttonsString?.tokenize(' ')
}

void solicitDefaultButton(String prefix) {
  String header = 'Default Button'
  if (prefix) { header << "for ${prefix}" }
  input(
    name: "${prefix}dflt",
    title: "${h3(header)}",
    type: 'enum',
    multiple: false,
    options: [*getButtonNames(prefix), 'not_applicable'],
    defaultValue: defaultButton(prefix) ?: 'not_applicable',
    submitOnChange: isDynamic(),
    required: true
  )
}

String defaultButton(String prefix) {
  return settings?."${prefix}dflt"
}

void solicitPbsgConfig(String pbsgName) {
  // OVERVIEW
  //   - This method is called from within an App page section{...}
  //   - The client first populates the names of the PBSG buttons.
  //   - Once the list of buttons exists, the user selects a
  //     default (dflt) button or 'not_applicable'
  //   - All exposed fields can be edited until data entry is "Done".
  paragraph(h1("Provide '${b(pbsgName)}' Configuration Data"))
  ArrayList buttonList = getButtonNames("${pbsgName}_")
  Integer buttonCount = buttonList?.size()
  // Provide feedback to the client (dynamically).
  ArrayList feedback = []
  switch (buttonCount) {
    case 1:
      feedback << h1('Oops only one button was detected')
      feedback << "${b('Buttons')}: ${buttonList}, ${b('Button Count')}: ${buttonCount}"
      // Deliberately fall through to the next case!
    case null:
      feedback << h2("Create at least two buttons for PBSG ${b(pbsgName)}")
      feedback << i("Enter button names ${b('delimited with spaces ')}")
      break
    default:
      feedback << h2("Button names for PBSG ${b(pbsgName)}")
      feedback << i('button names are delimited with spaces')
  }
  paragraph(h2(feedback.join('<br/>')))
  solicitButtonNames("${pbsgName}_")
  if (buttonCount > 2) {
    solicitDefaultButton("${pbsgName}_")
  }
}

////
//// SOLICIT PBSG ACTIONS AND BUILD AD HOC PBSG TEST SEQUENCES
////

void solicitNextAction(String pbsgName) {
  String header = 'Add a Test Action'
  if (pbsgName) { header << "for ${pbsgName}" }
  input(
    name: "${pbsgName}_nextAction",
    title: "${h3(header)}",
    type: 'enum',
    options: getTestActions(pbsgName),
    required: false,
    width: 3,
    submitOnChange: isDynamic()
  )
}

void removeNextAction(String pbsgName) {
  // Clear this setting when it is important to solicit fresh data entry
  // from a client without defaulting to the client's prior data entry.
  app.removeSetting("${pbsgName}_nextAction")
}

String getNextAction(String pbsgName) {
  return settings?."${pbsgName}_nextAction"
}

void solicitTestSequence(String testSequenceJson, String pbsgName) {
  String header = 'Save the Test Sequence'
  if (prefix) { header << "for ${pbsgName}" }
  input(
    name: "${pbsgName}_testSequence",
    title: "${h3(header)}",
    type: 'textarea',
    defaultValue: testSequenceJson,
    required: true,
    width: 9,
    submitOnChange: isDynamic()
  )
}

void removeTestSequence(String pbsgName = null) {
  // Clear this setting when it is important to solicit fresh data entry
  // from a client without defaulting to the client's prior data entry.
  app.removeSetting("${pbsgName}_testSequence")
}

ArrayList getTestSequence(String pbsgName) {
  return fromJson(settings?."${pbsgName}_testSequence")
}

////
//// SUPPORT FOR AD HOC PBSG TESTING
////

ArrayList getTestActions(String pbsgName) {
  // Develop Available Test Sequence Options
  ArrayList buttons = getButtonNames("${pbsgName}_")
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

void buildTestSequence(String pbsgName, ArrayList testOptions) {
  ArrayList testSeqList = getTestSequence(pbsgName) ?: []
  // Add the most recent 'next action' to the 'test sequence'.
  String nextAction = getNextAction(pbsgName)
  //logInfo('buildTestSequence#A', ['',
  //  "pbsgName: ${pbsgName}",
  //  "nextAction: ${nextAction}",
  //  "testSeqList: ${testSeqList}"
  //].join('<br/>'))
  if (nextAction) {
    testSeqList.add(nextAction)  // Add the latest item to the ArrayList
    //logInfo('buildTestSequence#B', ['',
    //  "pbsgName: ${pbsgName}",
    //  "testSeqList: ${testSeqList}"
    //].join('<br/>'))
    removeNextAction(pbsgName)     // Force fresh data soliciation
    removeTestSequence(pbsgName)   // Force fresh data soliciation
  }
  // Update the JSON version of the ArrayList
  String testSequenceJson = toJson(testSeqList)
  //??needed?? settings."${pbsgName}_testSequenceJson" = testSequenceJson
  // Solicit a new Next Action
  solicitNextAction(pbsgName)
  // Present the latest Test Squence Json (for settings preservation)
  solicitTestSequence(testSequenceJson, pbsgName)
}

void configurePbsgUsingParse(DevW pbsg) {
  // Per 'pbsg.groovy', there are two facilities for configuring a PBSG:
  //   (1) MANUALLY: Using the Hubitat GUI's device drilldown page.
  //   (2) PROGRAMMATICALLY: Using the PBSG's parse(String json) method.
  // This method demonstrates programatic configuration. It collects settings
  // solicited by 'page1_CreatePBSGs' into a Map. Then, passes a
  // JSON representation of the Map to the PBSG's parse(..) method.
  if (pbsg.name) {
    String prefix = "${pbsg.name}_"
    Map prefs = [
      buttons: getButtonNames(prefix).join(' '),  // ArrayList->String
      dflt: defaultButton(prefix),
      instType: 'pbsg',
      logLevel: 'INFO'
    ]
    pbsg.parse(toJson(prefs))
  } else {
    logError('configurePbsgUsingParse', 'Argument "pbsgName" was null')
  }
}

////
//// DEFINE THREE DEMO DATA COLLECTION PAGES
////

preferences {
  page(name: 'page1_CreatePBSGs', nextPage: 'page2_TestActions')
  page(name: 'page2_TestActions', nextPage: 'page3_TestSequences')
  page(name: 'page3_TestSequences')
}

Map page1_CreatePBSGs() {
  return dynamicPage(
    name: 'page1_CreatePBSGs',
    title: h1('Page 1 of 3 - Define PBSG Instance(s)'),
    uninstall: true
  ) {
    section {
      solicitPbsgNames()
      ArrayList pbsgNames = getPbsgNames()
      logInfo('page1_CreatePGSGs', "pbsgNames: ${pbsgNames}")
      pbsgNames.each{ pbsgName -> solicitPbsgConfig(pbsgName) }
    }
  }
}

Map page2_TestActions() {
  return dynamicPage(
    name: 'page2_TestActions',
    title: h1('Page 2 of 3 - List per-PBSG available Test Actions'),
    uninstall: true
  ) {
    section {
      ArrayList pbsgNames = settings.pbsgNames?.tokenize(' ')
      paragraph([
        'On the next page ...',
        "  You will create an ad hoc ${b('Test Sequence')} per PBSG",
        "    - A ${b('Test Sequence')} includes one or more ${b('Test Actions')}",
        "    - ${b('Test Actions')} can be repeated in a ${b('Test Sequence')}",
        '    - Press "Next" to continue'
      ].join('<br/>'))
      pbsgNames.each{ pbsgName ->
        ArrayList testActions = getTestActions(pbsgName)
        paragraph([
          h2("${b('Test Actions')} for the ${b(pbsgName)}"),
          "  - ${testActions.join('<br/>  - ')}"
        ].join('<br/>'))
      }
    }
  }
}

Map page3_TestSequences() {
  return dynamicPage(
    name: 'page3_TestSequences',
    title: h1('Page 3 of 3 - Create per-PBSG Test Sequences'),
    install: true,
    uninstall: true
  ) {
    section {
      paragraph([
        h2("To build a ${b('Test Sequence')}:"),
        "  - Select a ${b('Test Action')}",
        "  - Use ⏎ to append the ${b('Test Action')} to the ${b('Test Sequence')}",
        "  - Repeat until the ${b('Test Sequence')} is complete",
        '  - Press "Done" to build the PBSGs and run the Test Sequences',
        '  - Watch Hubitat logs for Test Actions and PBSG results'
      ].join('<br/>'))
      // Develop Available Test Sequence Options
      ArrayList pbsgNames = settings.pbsgNames?.tokenize(' ')
      pbsgNames.each{ pbsgName ->
        ArrayList testActions = getTestActions(pbsgName)
        buildTestSequence(pbsgName, testActions)
      }
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
    //   - Gather a PBSG configuration Map from data in settings.
    //   - Alternately, provide a PBSG configuration by brute force.
    //     Map config = [
    //        buttons: ...,  // ArrayList of button names
    //           dflt: ...,   // The default button name or null
    //       instType: ...,  // 'pbsg' in most cases
    //     ]
    DevW pbsg = getOrCreatePBSG(pbsgName)
    subscribe(pbsg, pbsgHandler, ['filterEvents': true])
    configurePbsgUsingParse(pbsg)
    // BUILD AND RUN ANY TEST SEQUENCES
    logInfo('initialize#NOTE_A', 'Entering build/run test sequences')
    ArrayList testSeqList = getTestSequence(pbsgName)
    Integer actionCnt = testSeqList.size()
    testSeqList?.eachWithIndex{ testAction, index ->
      logInfo('initialize#NOTE_B', "Taking Action ${index + 1} of ${actionCnt}: ${b(testAction)}")
      if (testAction == 'Activate_last_active') {
        //pbsg_ActivateLastActive(pbsg) && putPbsgState(pbsg)
        pbsg.activateLastActive()
      } else {
        ArrayList tokenizedAction = testAction.tokenize('_')
        if (tokenizedAction.size() == 2) {
          String target = tokenizedAction[0]  // Could be a button or a VSW
          String action = tokenizedAction[1]
          logInfo('initialize#NOTE_C', "target: ${target}, action ${action}")
          //logInfo('initialize#258', "${pbsg_State(pbsg)} -> ${button} : ${action}")
  ////
  //// Sunday Jun 9 - Need to invoke commands in Device
  //// command 'activate', [[
  ////   name: 'button',
  ////   type: 'string',
  ////   description: 'The button name to activate'
  //// ]]
  //// command 'deactivate', [[
  ////   name: 'button',
  ////   type: 'string',
  ////   description: 'The button name to deactivate'
  //// ]]
  //// command 'activateLastActive', [[
  ////
          switch (action) {
            case 'ButtonOn':
              //pbsg_ActivateButton(pbsg, target) && putPbsgState(pbsg)
      logInfo('initialize#NOTE_D', 'ButtonOn')
              pbsg.activate(target)
              break
            case 'ButtonOff':
              //pbsg_DeactivateButton(pbsg, target) && putPbsgState(pbsg)
      logInfo('initialize#NOTE_E', 'ButtonOff')
              pbsg.deactivate(target)
              break
            case 'VswOn':
              // Simulate an external VSW on
      logInfo('initialize#NOTE_F', 'VswOn')
              DevW vsw = getOrCreateVswWithToggle(pbsg.name, target)
              logInfo('initialize', "Turning ${vsw.name} on")
              vsw.on()
              pbsg = getPbsgState(pbsgName)  // Refresh pbsg state
              break
            case 'VswOff':
              // Simulate an external VSW off
      logInfo('initialize#NOTE_G', 'VswOff')
              DevW vsw = getOrCreateVswWithToggle(pbsg.name, target)
              logInfo('initialize', "Turning VSW ${vsw.name} off")
              vsw.off()
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
