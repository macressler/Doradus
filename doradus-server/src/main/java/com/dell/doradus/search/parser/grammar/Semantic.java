/*
 * Copyright (C) 2014 Dell, Inc.
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

package com.dell.doradus.search.parser.grammar;


public class Semantic implements GrammarRule {

    String name;

    public Semantic(String name) {
        this.name = name;
    }

    public Context Match(Context context) {
        context.items.add(new Literal(name, "semantic", -1));
        return context;
    }

    public String Name() {
        return "<Semantic: " + name + ">";
    }
}