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
package de.tudarmstadt.ukp.inception.recommendation.imls.ollama.prompt;

import java.util.stream.Stream;

import org.apache.uima.cas.CAS;

import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommendationEngine;

public class PerDocumentContextGenerator
    implements PromptContextGenerator
{

    @Override
    public Stream<PromptContext> generate(RecommendationEngine aEngine, CAS aCas, int aBegin,
            int aEnd)
    {
        var candidate = aCas.getDocumentAnnotation();
        var context = new PromptContext(candidate);
        context.set(VAR_TEXT, aCas.getDocumentText());
        return Stream.of(context);
    }
}
