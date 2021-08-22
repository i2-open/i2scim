/*
 * Copyright 2021.  Independent Identity Incorporated
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

package com.independentid.scim.protocol;

import com.independentid.scim.resource.Value;

import javax.validation.constraints.NotNull;

public class JsonPatchBuilder {
    JsonPatchRequest req;

    JsonPatchBuilder() {
        req = new JsonPatchRequest();
    }

    JsonPatchBuilder(JsonPatchOp op) {
        req = new JsonPatchRequest();
        req.addOperation(op);
    }

    public JsonPatchBuilder withAddOperation(String path, @NotNull Value value) {
        JsonPatchOp op = new JsonPatchOp(JsonPatchOp.OP_ACTION_ADD,path,value);
        req.addOperation(op);
        return this;
    }

    public JsonPatchBuilder withRemoveOperation(@NotNull String path) {
        JsonPatchOp op = new JsonPatchOp(JsonPatchOp.OP_ACTION_REMOVE,path,null);
        req.addOperation(op);
        return this;
    }

    public JsonPatchBuilder withReplaceOperation(String path, Value value) {
        JsonPatchOp op = new JsonPatchOp(JsonPatchOp.OP_ACTION_REPLACE,path,value);
        req.addOperation(op);
        return this;
    }

    public JsonPatchBuilder withOperation(@NotNull JsonPatchOp op) {
        req.addOperation(op);
        return this;
    }

    public JsonPatchRequest build() {
        return req;
    }
}
