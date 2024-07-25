/*
 * The MIT License
 *
 * Copyright (c) 2019, CloudBees, Inc.
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
function selectAll(anchor){
    _selectAllThat(anchor, '.checkbox-line');
}
function selectAllMigratable(anchor){
    _selectAllThat(anchor, '.checkbox-line.migratable-line.valid-line');
}
function selectAllValid(anchor){
    _selectAllThat(anchor, '.checkbox-line.valid-line');
}
function selectAllInvalid(anchor){
    _selectAllThat(anchor, '.checkbox-line.invalid-line');
}

function _selectAllThat(anchor, selector){
    var parent = anchor.closest('.legacy-copy-artifact');
    var allCheckBoxes = parent.querySelectorAll('.checkbox-line');
    var concernedCheckBoxes = parent.querySelectorAll(selector);
    
    checkTheDesiredOne(allCheckBoxes, concernedCheckBoxes);
}

function checkTheDesiredOne(allCheckBoxes, concernedCheckBoxes){
    var mustCheck = false;
    for(var i = 0; i < concernedCheckBoxes.length && !mustCheck ; i++){
        var checkBox = concernedCheckBoxes[i];
        if(!checkBox.checked){
            mustCheck = true;
        }
    }
    
    for(var i = 0; i < allCheckBoxes.length ; i++){
        var checkBox = allCheckBoxes[i];
        checkBox.checked = false;
    }
    
    for(var i = 0; i < concernedCheckBoxes.length ; i++){
        var checkBox = concernedCheckBoxes[i];
        checkBox.checked = mustCheck;
    }
    
    for(var i = 0; i < allCheckBoxes.length ; i++){
        var checkBox = allCheckBoxes[i];
        onCheckChanged(checkBox);
    }
}

function confirmAndIgnoreAllSelected(button){
    confirmAndSendRequest(button);
}

function confirmAndMigrateAllSelected(button){
    confirmAndSendRequest(button);
}

function confirmAndSendRequest(button){
    var parent = button.closest('.legacy-copy-artifact');
    var allCheckBoxes = parent.querySelectorAll('.checkbox-line');
    var allCheckedCheckBoxes = [];
    for(var i = 0; i < allCheckBoxes.length ; i++){
        var checkBox = allCheckBoxes[i];
        if(checkBox.checked){
            allCheckedCheckBoxes.push(checkBox);
        }
    }
    
    if(allCheckedCheckBoxes.length == 0){
        var nothingSelected = button.getAttribute('data-nothing-selected');
        alert(nothingSelected);
    }else{
        var confirmMessageTemplate = button.getAttribute('data-confirm-template');
        var confirmMessage = confirmMessageTemplate.replace('%num%', allCheckedCheckBoxes.length);
        if(confirm(confirmMessage)){
            var url = button.getAttribute('data-url');
            var selectedValues = [];
            
            for(var i = 0; i < allCheckedCheckBoxes.length ; i++){
                var checkBox = allCheckedCheckBoxes[i];
                var jobFrom = checkBox.getAttribute('data-job-from');
                var jobTo = checkBox.getAttribute('data-job-to');
                selectedValues.push({jobFrom: jobFrom, jobTo: jobTo});
            }
            
            var params = {values: selectedValues}
            fetch(url, {
                method: 'post',
                headers: crumb.wrap({
                    'Content-Type': 'application/json; charset=UTF-8',
                }),
                body: JSON.stringify(params),
            }).then((rsp) => {
                window.location.reload();
            });
        }
    }
}

function onLineClicked(event){
    var line = this;
    var checkBox = line.querySelector('.checkbox-line');
    // to allow click on checkbox to act normally
    if(event.target === checkBox){
        return;
    }
    checkBox.checked = !checkBox.checked;
    onCheckChanged(checkBox);
}

function onCheckChanged(checkBox){
    var line = checkBox.closest('tr');
    if(checkBox.checked){
        line.classList.add('selected');
    }else{
        line.classList.remove('selected');
    }
}

(function(){
    document.addEventListener("DOMContentLoaded", function() {
        var allLines = document.querySelectorAll('.legacy-copy-artifact table tr');
        for(var i = 0; i < allLines.length; i++){
            var line = allLines[i];
            if(!line.classList.contains('no-project-line')){
                line.onclick = onLineClicked;
            }
        }
        
        var allCheckBoxes = document.querySelectorAll('.checkbox-line');
        for(var i = 0; i < allCheckBoxes.length; i++){
            var checkBox = allCheckBoxes[i];
            checkBox.onchange = function(){ 
                onCheckChanged(this); 
            };
        }

        // Open and load detailed steps, just like help-link in the configuration forms.
        document.getElementById('detailed-steps').addEventListener(
            'click',
            function() {
                let body = document.querySelector(this.getAttribute('data-target'));

                if (body.style.display != 'block') {
                    body.style.display = 'block';
                    fetch(this.getAttribute('data-help-url')).then((rsp) => {
                        if (rsp.ok) {
                            rsp.text().then((responseText) => {
                                body.innerHTML = responseText;
                            });
                        } else {
                            body.innerHTML = (
                                '<b>ERROR</b>: Failed to load help file: '
                                + rsp.statusText
                            );
                        }
                    });
                } else {
                    body.style.display = "none";
                }
                return false;
            }
        );
    });
})();
