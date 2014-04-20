/*
 * The MIT License
 * 
 * Copyright (c) 2014 IKEDA Yasuyuki
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

/**
 * Allow switch selectbox and combobox.
 */

{

/**
 * Retrieve containers for each fields
 */
var getContainers = function(e) {
  // This would be placed like this:
  // <div class=" class="copyartifact-permalink-selectbox-switch-container copyartifact-permalink-selectbox-switch-selectbox">
  //    <select>
  // <div>
  // <div class=" class="copyartifact-permalink-selectbox-switch-container copyartifact-permalink-selectbox-switch-combobox">
  //    <input type="text"> <!--combobox-->
  // </div>
  // <div class=" class="copyartifact-permalink-selectbox-switch-container copyartifact-permalink-selectbox-switch-checkbox">
  //    <input type="checkbox"> <!-- this is e -->
  // </div>
  
  // find checkbox containing div.
  var container = $(e);
  while (container && !container.hasClassName("copyartifact-permalink-selectbox-switch-container")) {
    container = container.up();
  }
  
  if (!container) {
    // something starnge. give up.
    return;
  }
  
  container = container.up();
  
  // find each field container
  var checkboxContainer = container.down(".copyartifact-permalink-selectbox-switch-checkbox", 0);
  var comboboxContainer = container.down(".copyartifact-permalink-selectbox-switch-combobox", 0);
  var selectboxContainer = container.down(".copyartifact-permalink-selectbox-switch-selectbox", 0);
  
  if (!checkboxContainer || !comboboxContainer || !selectboxContainer) {
    // something starnge. give up.
    return;
  }
  
  return {
    checkbox: checkboxContainer,
    combobox: comboboxContainer,
    selectbox: selectboxContainer
  };
}

/**
 * Called when "input manyally" checkbox is toggled
 */
var onChangeSwitchCheckbox = function(e) {
  var containers = getContainers(e);
  if (!containers) {
    return;
  }
  
  // field-disabled attribute allows the field not to participate in the submitting json value.
  if(e.checked) {
    // input manually
    containers.selectbox.setAttribute("field-disabled", "true");
    containers.selectbox.hide();
    containers.combobox.removeAttribute("field-disabled");
    containers.combobox.show();
  } else {
    // use selectbox
    containers.combobox.setAttribute("field-disabled", "true");
    containers.combobox.hide();
    containers.selectbox.removeAttribute("field-disabled");
    containers.selectbox.show();
  }
}

/**
 * Decide which to display initially
 * If values of selectbox and combobox do not match after loading selectbox items, enable combobox.
 */
var onFilledSwitchSelect = function(e) {
  var containers = getContainers(e);
  if (!containers) {
    return;
  }
  
  // check whether already initialized.
  var checkbox = containers.checkbox.down("input");
  if (!checkbox.getAttribute("wait-initialize")) {
    return;
  }
  checkbox.removeAttribute("wait-initialize");
  
  var select = containers.selectbox.down("select");
  if (!select) {
    return;
  }
  
  var combobox = containers.combobox.down("input");
  if (!combobox) {
    return;
  }
  
  checkbox.checked = (combobox.value != "" && combobox.value != select.value);
  onChangeSwitchCheckbox(checkbox);
}

Behaviour.specify(".copyartifact-permalink-selectbox-switch", "copyartifactPermalinkSelectboxSwitch", 0, function (e) {
  $(e).observe("click", function(event){onChangeSwitchCheckbox(this);});
  var containers = getContainers(e);
  if (!containers) {
    return;
  }
  var select = containers.selectbox.down("select");
  if (!select) {
    return;
  }
  $(e).setAttribute("wait-initialize", "true");
  $(select).observe("filled", function(event){onFilledSwitchSelect(this)});
});

}
