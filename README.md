# DeepDriftCorrect

This is a MicroManager hardware autofocus plugin for live lateral and axial microscopy drift correction using the cross correlated signal from infrared brightfield microscopy running independently on a second camera, based on the principle described in McGorty et al, Optical Nanoscopy 2013. 

The version in the v1.0.0 release is associated with the Whitley et al Nat Comms 2021 paper and was originally developed between the Henriques and Holden laboratories. This plugin will perform lateral drift correction and/or axial autofocus at depth using the infrared camera while imaging a sample with visible fluorescence light.

We have now updated the plugin to enable lateral drift correction and axial autofocus to be performed with separate systems as a v1.1.0 release. For example, users can use this system for lateral drift correction while simultaneously using a separate one (e.g. CRISP) for axial autofocus. The background subtraction and calibration routines have also been made more robust, and the GUI has been made more user-friendly. Further changes have been made to improve distributability. Full installation and accompanying hardware instructions are available on the <a href="https://holdenlab.github.io/LifeHackWebsite/">LifeHack microscope website</a>.

LICENSING INFORMATION All files are distributed under the GPLv3 and (c) 2023 Kevin Whitley, Josh Edwards, Seamus Holden, Newcastle University, Pedro Almada, Ricardo Henriques, University College London unless otherwise stated. See LICENSE.txt for full terms.
