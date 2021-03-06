/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.timeline.actions;

import javafx.event.ActionEvent;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import org.controlsfx.control.action.Action;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.TimeLineController;

/**
 *
 */
//TODO: This and the corresponding imageanalyzer action are identical except for the type of the controller...  abstract something! -jm
public class Forward extends Action {

    private static final Image BACK_IMAGE = new Image("/org/sleuthkit/autopsy/timeline/images/arrow.png", 16, 16, true, true, true); // NON-NLS

    private final TimeLineController controller;

    public Forward(TimeLineController controller) {
        super(NbBundle.getMessage(Forward.class, "Forward.action.name.text"));
        setGraphic(new ImageView(BACK_IMAGE));
        setAccelerator(new KeyCodeCombination(KeyCode.RIGHT, KeyCodeCombination.ALT_DOWN));
        this.controller = controller;
        disabledProperty().bind(controller.getCanAdvance().not());
        setEventHandler((ActionEvent t) -> {
            controller.advance();
        });
    }
}
