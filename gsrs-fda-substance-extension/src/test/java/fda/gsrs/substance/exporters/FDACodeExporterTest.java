package fda.gsrs.substance.exporters;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fda.gsrs.substance.exporters.FDACodeExporterFactory;
import fda.gsrs.substance.exporters.FDACodeExporter;
import ix.core.models.Group;
import ix.ginas.exporters.DefaultParameters;
import ix.ginas.exporters.OutputFormat;
import ix.ginas.modelBuilders.SubstanceBuilder;
import ix.ginas.models.v1.Code;
import ix.ginas.models.v1.Substance;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class FDACodeExporterTest {

    List<String> substanceNames = Arrays.asList("Substance One", "Substance #1");

    @Test
    public void testExportCodes() throws IOException {
        FDACodeExporterFactory factory = new FDACodeExporterFactory();
        factory.setPrimaryCodeSystem("BDNUM");
        Assertions.assertEquals("BDNUM", factory.getPrimaryCodeSystem());
        ByteArrayOutputStream byteArrayInputStream = new ByteArrayOutputStream(4096);
        File outputFile = File.createTempFile("temp-codes", "txt");
        if(outputFile.exists()) {
            outputFile.delete();
            log.trace("file existed and was deleted");
        }
        FileOutputStream fos = new FileOutputStream(outputFile);
        ObjectNode details = JsonNodeFactory.instance.objectNode();
        // details.put(FDACodeExporterFactory.CODE_PARAMETERS, true);
        // details.put(FDACodeExporterFactory.NAME_PARAMETERS, true);
        OutputFormat outputFormat = new OutputFormat("codes.txt", "codes.txt");
        BufferedOutputStream outputStream= new BufferedOutputStream(fos);
        DefaultParameters parameters = new DefaultParameters(outputFormat,false, details);
        FDACodeExporter exporter = (FDACodeExporter) factory.createNewExporter(outputStream, parameters );
        exporter.export(createSubstanceWithNamesAndCodes());
        exporter.close();
        Assertions.assertTrue(outputFile.exists());
        String fileData= Files.lines(outputFile.toPath(),StandardCharsets.UTF_8).collect(Collectors.joining("\n"));
        System.out.println("fileData: " + fileData);
        Assertions.assertTrue(fileData.contains("001123AB"));
        // Assertions.assertTrue(substanceNames.stream().allMatch(s->fileData.contains(s)));
    }
    @Test
    public void testExportCodesNoPrimaryCodeSystem() throws IOException {
        FDACodeExporterFactory factory = new FDACodeExporterFactory();
        Assertions.assertEquals(null, factory.getPrimaryCodeSystem());
        ByteArrayOutputStream byteArrayInputStream = new ByteArrayOutputStream(4096);
        File outputFile = File.createTempFile("temp-codes", "txt");
        if(outputFile.exists()) {
            outputFile.delete();
            log.trace("file existed and was deleted");
        }
        FileOutputStream fos = new FileOutputStream(outputFile);
        ObjectNode details = JsonNodeFactory.instance.objectNode();
        // details.put(FDACodeExporterFactory.CODE_PARAMETERS, true);
        // details.put(FDACodeExporterFactory.NAME_PARAMETERS, true);
        OutputFormat outputFormat = new OutputFormat("codes.txt", "codes.txt");
        BufferedOutputStream outputStream= new BufferedOutputStream(fos);
        DefaultParameters parameters = new DefaultParameters(outputFormat,false, details);
        FDACodeExporter exporter = (FDACodeExporter) factory.createNewExporter(outputStream, parameters );
        exporter.export(createSubstanceWithNamesAndCodes());
        exporter.close();
        Assertions.assertTrue(outputFile.exists());
        String fileData= Files.lines(outputFile.toPath(),StandardCharsets.UTF_8).collect(Collectors.joining("\n"));
        System.out.println("fileData: " + fileData);
        Assertions.assertTrue(fileData.contains("001123AB"));
        Assertions.assertEquals(null, factory.getPrimaryCodeSystem());

        // Assertions.assertTrue(substanceNames.stream().allMatch(s->fileData.contains(s)));
    }


    private Substance createSubstanceWithNamesAndCodes() {
        SubstanceBuilder builder = new SubstanceBuilder();
        substanceNames.forEach(builder::addName);
        Code b1= new Code();
        b1.codeSystem="BDNUM";
        b1.code="001123AB";
        b1.type = "PRIMARY";
        b1.codeText = "codetext1";
        b1.comments = "Hi\ncomment1";

        builder.addCode(b1);

        Code b2= new Code();
        b2.codeSystem="UNII";
        b2.type = "DEFAULT";
        b2.code="XYZBEFCHI1";
        b2.codeText = "codetext2";
        b2.comments = "Hi\ncomment2";
        Group gA = new Group();
        gA.name = "Protected";
        Group gB = new Group();
        gB.name = "admin";
        Set<Group> groupSet = new HashSet<Group>();
        groupSet.add(gA);
        groupSet.add(gB);
        b2.setAccess(groupSet);
        builder.addCode(b2);
        Substance s =  builder.build();
        s.approvalID = "XYZBEFCHI1";
        return s;
    }
}
