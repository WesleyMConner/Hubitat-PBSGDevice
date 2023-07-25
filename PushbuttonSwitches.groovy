// ---------------------------------------------------------------------------------
// P U S H B U T T O N   S W I T C H E S
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

definition(
  name: "Pushbutton Switches",
  namespace: 'wesmc',
  author: 'WesleyMConner',
  description: 'Track (functionally-independent) Pushbutton Switch Instances.',
  category: '',           // Not supported as of Q3'23
  iconUrl: '',            // Not supported as of Q3'23
  iconX2Url: '',          // Not supported as of Q3'23
  iconX3Url: '',          // Not supported as of Q3'23
  singleInstance: true
)

// ---------------------------------------------------------------------
// F U N C T I O N S   U S E D   I N   C L I E N T   I N T E R F A C E S
// ---------------------------------------------------------------------
BLACK = 'rgba(0, 0, 0, 1.0)'
BLUE = 'rgba(51, 92, 255, 1.0)'
LIGHT_GREY = 'rgba(180, 180, 180, 1.0)'
RED = 'rgba(51, 51, 51, 1.0)'

// -------------------------------
// C L I E N T   I N T E R F A C E
// -------------------------------
preferences {
  page name: "monoPage", title: "", install: true, uninstall: true
}

String heading(String s) {
  HEADING_CSS = "font-size: 2em; font-weight: bold;"
  return """<span style="${HEADING_CSS}">${s}</span>"""
}

String emphasis(String s) {
  EMPHASIS_CSS = "font-size: 1.3em; color: ${BLUE}; margin-left: 20px;"
  return """<br/><span style="${EMPHASIS_CSS}">${s}</span><br/>"""
}

String normal(String s) {
  NORMAL_CSS = "font-size: 1.1em;"
  return """<span style="${NORMAL_CSS}">${s}</span>"""
}

String bullet(String s) {
  BULLET_CSS = "font-size: 1em; margin-left: 20px;"
  return """<span style="${BULLET_CSS}">&#x2022;&nbsp;&nbsp;${s}</span>"""
}

String comment(String s) {
  COMMENT_CSS = "font-size: 0.8em; color: ${LIGHT_GREY}"
  return """<span style="${COMMENT_CSS}">${s}</span>"""
}

Map monoPage() {
  return dynamicPage(name: "monoPage") {
    if (app.getInstallationState() != 'COMPLETE') {
      section {
        paragraph([
          heading('Pushbutton Switches'),
          emphasis('Before you can create <b>Pushbutton Switch Instance(s)</b> ...'),
          normal('Push the <b>Done</b> button.'),
          bullet('This <em>parent application</em> will be installed.'),
          bullet('The parent groups <b>Pushbutton Switch Instances</b> (children) together.')
        ].join('\n'))
      } 
    } else {
      section {
        paragraph '<span style="font-size: 2em; font-weight: bold;">Pushbutton Switches</span>'
        app(
          name: "childAppInstances",
          appName: "Pushbutton Switch Instance",
          namespace: "wesmc",
          title: "<b>Add a new Pushbutton Switch Group</b>",
          multiple: true
        )
        paragraph "Pushbutton Switches - @wesmc, <a href='https://tbd.com/tbd/' \
            target='_blank'> Click for more info.</a>"
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
  initialize()
}

void initialize() {
  log.info """initialize() with ${childApps.size()} Pushbutton Groups</br>
    ${childApps.each({ child -> "&#x2022;&nbsp;${child.label}" })}
  """
}