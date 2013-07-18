/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Daniel Dyer, Yahoo! Inc., Alan Harder, InfraDNA, Inc.
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
Behaviour.register({"A.copy-artifact-migration-from0125": function(e) {
    e.onclick = function() {
        
        // find help area.
        var parentDiv = findAncestor(this, "DIV");
        if(parentDiv == null) {
            return false;
        }
        var div = parentDiv;
        do {
            div = $(div).next();
        } while (div != null && !Element.hasClassName(div,"help"));
        if(div == null) {
            return false;
        }
        
        if (div.style.display != "block") {
            // make it visible
            div.style.display = "block";
            new Ajax.Request(this.getAttribute("helpURL"), {
                method : 'get',
                onSuccess : function(x) {
                    div.innerHTML = x.responseText;
                    layoutUpdateCallback.call();
                },
                onFailure : function(x) {
                    div.innerHTML = "<b>ERROR</b>: Failed to load help file: " + x.statusText;
                    layoutUpdateCallback.call();
                }
            });
        } else {
            // hide
            div.style.display = "none";
            layoutUpdateCallback.call();
        }
        return false;
    };
    e = null; // avoid memory leak
}});
