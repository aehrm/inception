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
package de.tudarmstadt.ukp.inception.pdfeditor2.format;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.uima.cas.CAS;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FloatArray;
import org.apache.uima.jcas.cas.IntegerArray;
import org.dkpro.core.api.io.JCasResourceCollectionReader_ImplBase;
import org.dkpro.core.api.pdf.type.PdfChunk;
import org.dkpro.core.api.pdf.type.PdfPage;

import de.tudarmstadt.ukp.inception.pdfeditor2.visual.VisualPDFTextStripper;
import de.tudarmstadt.ukp.inception.pdfeditor2.visual.model.VChunk;
import de.tudarmstadt.ukp.inception.pdfeditor2.visual.model.VGlyph;
import de.tudarmstadt.ukp.inception.pdfeditor2.visual.model.VModel;
import de.tudarmstadt.ukp.inception.pdfeditor2.visual.model.VPage;

public class VisualPdfReader
    extends JCasResourceCollectionReader_ImplBase
{
    @Override
    public void getNext(JCas aJCas) throws IOException, CollectionException
    {
        Resource resource = nextFile();
        initCas(aJCas, resource, null);

        StringWriter textBuffer = new StringWriter();
        VModel vModel;

        try (InputStream is = resource.getInputStream()) {
            try (PDDocument doc = PDDocument.load(is)) {
                var stripper = new VisualPDFTextStripper();
                stripper.writeText(doc, textBuffer);
                vModel = stripper.getVisualModel();
            }
        }

        aJCas.setDocumentText(textBuffer.toString());

        visualModelToCas(vModel, aJCas);
    }

    public static void visualModelToCas(VModel aVModel, JCas aJCas)
    {
        for (VPage vPage : aVModel.getPages()) {
            PdfPage pdfPage = new PdfPage(aJCas, vPage.getBegin(), vPage.getEnd());
            pdfPage.setPageNumber(vPage.getIndex());
            pdfPage.setWidth(vPage.getWidth());
            pdfPage.setHeight(vPage.getHeight());
            pdfPage.addToIndexes();

            for (VChunk vChunk : vPage.getChunks()) {
                FloatArray glyphArray = new FloatArray(aJCas, vChunk.getGlyphs().size());
                IntegerArray charArray = new IntegerArray(aJCas, vChunk.getGlyphs().size());

                int i = 0;
                for (VGlyph vGlyph : vChunk.getGlyphs()) {
                    charArray.set(i, vGlyph.getEnd() - vGlyph.getBegin());
                    glyphArray.set(i, vGlyph.getBase());
                    i++;
                }

                PdfChunk pdfChunk = new PdfChunk(aJCas, vChunk.getBegin(), vChunk.getEnd());
                pdfChunk.setD(vChunk.getDir());
                pdfChunk.setX(vChunk.getX());
                pdfChunk.setY(vChunk.getY());
                pdfChunk.setW(vChunk.getW());
                pdfChunk.setH(vChunk.getH());
                pdfChunk.setC(charArray);
                pdfChunk.setG(glyphArray);
                pdfChunk.addToIndexes();
            }
        }
    }

    public static VModel visualModelFromCas(CAS cas, List<PdfPage> pdfPages)
    {
        VModel vModel;
        List<VPage> vPages = new ArrayList<>();
        for (PdfPage pdfPage : pdfPages) {
            List<VChunk> vChunks = new ArrayList<>();
            for (var pdfChunk : cas.select(PdfChunk.class).coveredBy(pdfPage)) {
                float d = pdfChunk.getD();
                List<VGlyph> vGlyphs = new ArrayList<>();
                IntegerArray charWidths = pdfChunk.getC();
                FloatArray glyphStarts = pdfChunk.getG();
                float b0, le, ef;
                switch ((int) d) {
                case 0:
                    b0 = pdfChunk.getX();
                    le = pdfChunk.getW();
                    ef = 1;
                    break;
                case 90:
                    b0 = pdfChunk.getY();
                    le = pdfChunk.getH();
                    ef = 1;
                    break;
                case 180:
                    b0 = pdfChunk.getX() + pdfChunk.getW();
                    le = pdfChunk.getW();
                    ef = -1;
                    break;
                case 270:
                    b0 = pdfChunk.getY() + pdfChunk.getH();
                    le = pdfChunk.getH();
                    ef = -1;
                    break;
                default:
                    throw new IllegalStateException(
                            "Only directions 0, 90, 180, 270 supported: " + d);
                }
                int begin = pdfChunk.getBegin();
                int glyphCount = glyphStarts.size();
                for (int i = 0; i < glyphCount; i++) {
                    var b = glyphStarts.get(i);
                    var isLastGlyphInChunk = i == glyphCount - 1;
                    var e = ((isLastGlyphInChunk ? b0 + le : glyphStarts.get(i + 1)) - b) * ef;
                    // assert e >= 0;
                    float x = (d == 0 || d == 180) ? b : pdfChunk.getX();
                    float y = (d == 0 || d == 180) ? pdfChunk.getY() : b;
                    float w = (d == 0 || d == 180) ? e : pdfChunk.getW();
                    float h = (d == 0 || d == 180) ? pdfChunk.getH() : e;

                    int end = begin + charWidths.get(i);
                    String coveredText = cas.getDocumentText().substring(begin, end);

                    vGlyphs.add(
                            new VGlyph(begin, pdfPage.getPageNumber(), coveredText, d, x, y, w, h));

                    begin += charWidths.get(i);
                }

                vChunks.add(new VChunk(pdfChunk.getBegin(), pdfChunk.getEnd(),
                        pdfChunk.getCoveredText(), pdfChunk.getD(), pdfChunk.getX(), pdfChunk.getY(),
                        pdfChunk.getW(), pdfChunk.getH(), vGlyphs));
            }

            vPages.add(new VPage(pdfPage.getPageNumber(), pdfPage.getWidth(), pdfPage.getHeight(),
                    pdfPage.getBegin(), pdfPage.getEnd(), pdfPage.getCoveredText(), vChunks));
        }
        vModel = new VModel(vPages);
        return vModel;
    }

}
