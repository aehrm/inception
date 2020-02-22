/*
 * Copyright 2020
 * Ubiquitous Knowledge Processing (UKP) Lab and FG Language Technology
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.clarin.webanno.api.annotation.layer;

import static java.util.Arrays.asList;

import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.EnumChoiceRenderer;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.spring.injection.annot.SpringBean;

import de.agilecoders.wicket.extensions.markup.html.bootstrap.form.select.BootstrapSelect;
import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationSchemaService;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.coloring.ColoringRulesConfigurationPanel;
import de.tudarmstadt.ukp.clarin.webanno.model.AnchoringMode;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationLayer;
import de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaBehavior;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.SurfaceForm;

public class SpanLayerTraitsEditor
    extends Panel
{
    private static final long serialVersionUID = -9082045435380184514L;

    private static final String MID_FORM = "form";

    private @SpringBean AnnotationSchemaService annotationService;
    private @SpringBean LayerSupportRegistry layerSupportRegistry;

    private String layerSupportId;
    private CompoundPropertyModel<SpanLayerTraits> traitsModel;
    private IModel<AnnotationLayer> layerModel;
    private ColoringRulesConfigurationPanel coloringRules;
    private DropDownChoice<AnchoringMode> anchoringMode;

    public SpanLayerTraitsEditor(String aId, SpanLayerSupport aFS, IModel<AnnotationLayer> aLayer)
    {
        super(aId, aLayer);

        layerSupportId = aFS.getId();

        layerModel = aLayer;
        traitsModel = CompoundPropertyModel
                .of(getLayerSupport().readTraits(layerModel.getObject()));

        Form<SpanLayerTraits> form = new Form<SpanLayerTraits>(MID_FORM, traitsModel)
        {
            private static final long serialVersionUID = -3109239605783291123L;

            @Override
            protected void onSubmit()
            {
                super.onSubmit();

                getLayerSupport().writeTraits(layerModel.getObject(), traitsModel.getObject());
            }
        };

        coloringRules = newColoringRulesConfigurationPanel(aLayer);
        form.add(coloringRules);
        
        form.add(anchoringMode = new BootstrapSelect<AnchoringMode>("anchoringMode"));
        anchoringMode.setModel(PropertyModel.of(layerModel, "anchoringMode"));
        anchoringMode.setChoiceRenderer(new EnumChoiceRenderer<>(this));
        anchoringMode.setChoices(asList(AnchoringMode.values()));
        anchoringMode.add(LambdaBehavior.onConfigure(_this -> {
            AnnotationLayer layer = layerModel.getObject();
            _this.setEnabled(
                    // Surface form must be locked to token boundaries for CONLL-U writer to work.
                    !SurfaceForm.class.getName().equals(layer.getName()) &&
                    // Not configurable for layers that attach to tokens (currently
                    // that is the only layer on which we use the attach feature)
                    layer.getAttachFeature() == null);
        }));

        form.setOutputMarkupPlaceholderTag(true);
        add(form);
    }
    
    private ColoringRulesConfigurationPanel newColoringRulesConfigurationPanel(
            IModel<AnnotationLayer> aFeature)
    {
        ColoringRulesConfigurationPanel panel = new ColoringRulesConfigurationPanel("coloringRules",
                aFeature, traitsModel.bind("coloringRules.rules"));
        panel.setOutputMarkupId(true);
        return panel;
    }

    private SpanLayerSupport getLayerSupport()
    {
        return (SpanLayerSupport) layerSupportRegistry.getLayerSupport(layerSupportId);
    }
}
