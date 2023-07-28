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

#include wesmc.UtilsLibrary

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

// -------------------------------
// C L I E N T   I N T E R F A C E
// -------------------------------
preferences {
  page name: "monoPage", title: "", install: true, uninstall: true
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