/*
 * Licensed to the Technische Universität Darmstadt under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The Technische Universität Darmstadt 
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.
 *  
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.clarin.webanno.support.extensionpoint;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.commons.lang3.Validate;

public abstract class CachingContextLookupExtensionPoint_ImplBase<C, E extends Extension<C>>
    extends ExtensionPoint_ImplBase<C, E>
    implements ContextLookupExtensionPoint<C, E>
{
    private final Map<Object, E> cache = new HashMap<>();

    private final Function<C, Object> keyExtractor;

    public CachingContextLookupExtensionPoint_ImplBase(List<E> aExtensions,
            Function<C, Object> aKeyExtractor)
    {
        super(aExtensions);
        keyExtractor = aKeyExtractor;
    }

    @Override
    public synchronized <X extends E> X findExtension(C aContext)
    {
        Validate.notNull(aContext, "Extension lookup key must be specified");

        // This method is called often during rendering, so we try to make it fast by caching
        // the supports by feature. Since the set of annotation features is relatively stable,
        // this should not be a memory leak - even if we don't remove entries if annotation
        // features would be deleted from the DB.
        E extension = null;

        Object keyId = keyExtractor.apply(aContext);
        if (keyId != null) {
            extension = cache.get(keyId);
        }

        if (extension == null) {
            for (E s : getExtensions()) {
                if (s.accepts(aContext)) {
                    extension = s;
                    if (keyId != null) {
                        // Store feature in the cache, but only when it has an ID, i.e. it has
                        // actually been saved.
                        cache.put(keyId, s);
                    }
                    break;
                }
            }
        }

        if (extension == null) {
            throw new IllegalArgumentException(
                    "Unable to find suitable extension for handling [" + aContext + "]");
        }

        return (X) extension;
    }
}
