package example.substance.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import gsrs.cache.GsrsCache;
import gsrs.module.substance.repository.SubstanceRepository;
import gsrs.service.GsrsEntityService;
import ix.core.models.Keyword;
import com.fasterxml.jackson.databind.JsonNode;
import example.GsrsModuleSubstanceApplication;
import gsrs.module.substance.controllers.SubstanceLegacySearchService;
import gsrs.module.substance.indexers.SubstanceDefinitionalHashIndexer;
import gsrs.module.substance.services.DefinitionalElementFactory;
import gsrs.springUtils.AutowireHelper;
import gsrs.startertests.TestGsrsValidatorFactory;
import gsrs.startertests.TestIndexValueMakerFactory;
import gsrs.substances.tests.AbstractSubstanceJpaFullStackEntityTest;
import gsrs.validator.DefaultValidatorConfig;
import gsrs.validator.ValidatorConfig;
import ix.core.chem.StructureProcessor;
import ix.core.validator.ValidationResponse;
import ix.ginas.models.EmbeddedKeywordList;
import ix.ginas.models.v1.Name;
import ix.ginas.models.v1.Substance;
import ix.ginas.utils.validation.validators.StandardNameDuplicateValidator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.test.context.support.WithMockUser;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = GsrsModuleSubstanceApplication.class)
@WithMockUser(username = "admin", roles = "Admin")
public class StandardNameDuplicateValidatorViaIndexerTest extends AbstractSubstanceJpaFullStackEntityTest {

        @Autowired
        private SubstanceRepository substanceRepository;

        @Autowired
        private SubstanceLegacySearchService searchService;

        @Autowired
        private DefinitionalElementFactory definitionalElementFactory;

        @Autowired
        private TestIndexValueMakerFactory testIndexValueMakerFactory;

        @Autowired
        StructureProcessor structureProcessor;

        @Autowired
        private TestGsrsValidatorFactory factory;

        @Autowired
        private GsrsCache cache;

        private String fileName = "rep18.gsrs";

        @BeforeEach
        public void clearIndexers() throws IOException {
        SubstanceDefinitionalHashIndexer hashIndexer = new SubstanceDefinitionalHashIndexer();
        AutowireHelper.getInstance().autowire(hashIndexer);
        testIndexValueMakerFactory.addIndexValueMaker(hashIndexer);
        {
        ValidatorConfig config = new DefaultValidatorConfig();
        // config.setValidatorClass(ChemicalValidator.class);
        config.setNewObjClass(Substance.class);
        factory.addValidator("substances", config);
        }

        File dataFile = new ClassPathResource(fileName).getFile();
        // loadGsrsFile(dataFile);
        }

        @Test
        public void testCheckStdNameForDuplicateInOtherRecordsViaIndexer() {
                StandardNameDuplicateValidator validator = new StandardNameDuplicateValidator();
                validator.setTransactionManager(transactionManager);
                validator.setSearchService(searchService);
                validator.setSubstanceRepository(substanceRepository);

                Substance s1 = null;
                Substance s2 = null;
                // how to load substances with a preset uuid?
                String id1 = "a7dcc059-7f47-4815-8444-2157381b8f17";
                String id2 = "b7dcc059-7f47-4815-8444-2157381b8f18";
                String name1 = "Test1";
                String stdName1 = "Test1 Std";
                String name2 = "Test2";
                String stdName2 = "Test1 Std";  // The 1 is on purpose

                String j1 = "{\"substanceClass\": \"concept\", \"names\": [{\"name\": \"__NAME_1__\", \"stdName\": \"__STDNAME_1__\", \"references\": [\"__ID_1__\"]}], \"references\": [{\"uuid\": \"__ID_1__\", \"citation\": \"Some Citatation __NAME_1__\", \"docType\": \"WEBSITE\", \"publicDomain\": true}], \"access\": [\"protected\"]}";
                String j2 = "{\"substanceClass\": \"concept\", \"names\": [{\"name\": \"__NAME_2__\", \"stdName\": \"__STDNAME_2__\", \"references\": [\"__ID_2__\"]}], \"references\": [{\"uuid\": \"__ID_2__\", \"citation\": \"Some Citatation __NAME_2__\", \"docType\": \"WEBSITE\", \"publicDomain\": true}], \"access\": [\"protected\"]}";
                j1 = j1.replaceAll("__ID_1__", id1);
                j2 = j2.replaceAll("__ID_2__", id2);
                j1 = j1.replaceAll("__NAME_1__", name1);
                j2 = j2.replaceAll("__NAME_2__", name2);
                j1 = j1.replaceAll("__STDNAME_1__", stdName1);
                j2 = j2.replaceAll("__STDNAME_2__", stdName2);

                s1 = loadSubstanceFromJsonString(j1);
                s2 = loadSubstanceFromJsonString(j2);

                assertEquals(substanceRepository.count(), 2);

                List<Substance> substances1 = validator.findIndexedSubstancesByStdName(stdName2);
                assertEquals(substances1.size(), 2);
                Substance otherSubstance1 = validator.checkStdNameForDuplicateInOtherRecordsViaIndexer(s2, stdName2);
                assertEquals(otherSubstance1.getUuid(), s1.getUuid());

                String wontBeFound = "Strange thing";
                List<Substance> substances = validator.findIndexedSubstancesByStdName(wontBeFound);
                assertEquals(substances.size(), 0);
                Substance otherSubstance = validator.checkStdNameForDuplicateInOtherRecordsViaIndexer(s2, wontBeFound);
                assertNull(otherSubstance);
        }

        @Test
        public void testStdNameDuplicateInOtherRecordViaIndexer() {
                StandardNameDuplicateValidator validator = new StandardNameDuplicateValidator();
                validator.setTransactionManager(transactionManager);
                validator.setSearchService(searchService);
                validator.setSubstanceRepository(substanceRepository);
                validator.setCheckDuplicateInOtherRecord(true);
                validator.setOnDuplicateInOtherRecordShowError(true);

                Substance s1 = null;
                Substance s2 = null;
                // how to load substances with a preset uuid?
                String id1 = "a7dcc059-7f47-4815-8444-2157381b8f17";
                String id2 = "b7dcc059-7f47-4815-8444-2157381b8f18";
                String name1 = "Test1";
                String stdName1 = "Test1 Std";
                String name2 = "Test2";
                String stdName2 = "Test1 Std";  // The 1 is on purpose

                String j1 = "{\"substanceClass\": \"concept\", \"names\": [{\"name\": \"__NAME_1__\", \"stdName\": \"__STDNAME_1__\", \"references\": [\"__ID_1__\"]}], \"references\": [{\"uuid\": \"__ID_1__\", \"citation\": \"Some Citatation __NAME_1__\", \"docType\": \"WEBSITE\", \"publicDomain\": true}], \"access\": [\"protected\"]}";
                String j2 = "{\"substanceClass\": \"concept\", \"names\": [{\"name\": \"__NAME_2__\", \"stdName\": \"__STDNAME_2__\", \"references\": [\"__ID_2__\"]}], \"references\": [{\"uuid\": \"__ID_2__\", \"citation\": \"Some Citatation __NAME_2__\", \"docType\": \"WEBSITE\", \"publicDomain\": true}], \"access\": [\"protected\"]}";
                j1 = j1.replaceAll("__ID_1__", id1);
                j2 = j2.replaceAll("__ID_2__", id2);
                j1 = j1.replaceAll("__NAME_1__", name1);
                j2 = j2.replaceAll("__NAME_2__", name2);
                j1 = j1.replaceAll("__STDNAME_1__", stdName1);
                j2 = j2.replaceAll("__STDNAME_2__", stdName2);

                s1 = loadSubstanceFromJsonString(j1);
                s2 = loadSubstanceFromJsonString(j2);

                assertEquals(substanceRepository.count(), 2);

                ValidationResponse<Substance> response = validator.validate(s2, null);
                response.getValidationMessages().forEach(vm->{
                        if(vm.getMessage().contains(validator.getDUPLICATE_IN_OTHER_RECORD_MESSAGE_TEST_FRAGMENT())) {
                                Assertions.assertEquals("ERROR", vm.getMessageType().toString());
                        }
                });
        }




        public Substance loadSubstanceFromJsonString(String jsonText) {
                Substance substance = null;
                ObjectMapper mapper = new ObjectMapper();
                JsonNode json = null;
                try {
                        json = mapper.readTree(jsonText);
                } catch (Exception ex) {
                        ex.printStackTrace();
                }
                try {
                        GsrsEntityService.CreationResult<Substance> result= substanceEntityService.createEntity(json, true);
                        substance = result.getCreatedEntity();
                } catch (IOException e) {
                        e.printStackTrace();
                        return null;
                }
                return substance;
        }

        @Test
        public void testDuplicateInSameRecordShowWarning() {
                boolean showError = false;
                StandardNameDuplicateValidator validator = new StandardNameDuplicateValidator();
                validator.setCheckDuplicateInSameRecord(true);
//                validator.setCheckDuplicateInOtherRecord(false);
                validator.setOnDuplicateInSameRecordShowError(showError);
                validator.setSubstanceRepository(substanceRepository);
                validator.setTransactionManager(transactionManager);
                validator.setSearchService(searchService);
                validator.setCache(cache);
                Substance s1 = new Substance();
                Name name1 = new Name();
                name1.name ="TestSame1";
                name1.stdName ="TestSame1 std";
                if (name1.languages == null) {
                        name1.languages = new EmbeddedKeywordList();
                }
                name1.languages.add(new Keyword("en"));
                name1.languages.add(new Keyword("fr"));
                s1.names.add(name1);
                Name name2 = new Name();
                name2.name ="TestSame2";
                name2.stdName ="TestSame1 std";  // 1 on purpose
                if (name1.languages == null) {
                        name1.languages = new EmbeddedKeywordList();
                }
                name2.languages.add(new Keyword("en"));
                name2.languages.add(new Keyword("fr"));
                s1.names.add(name2);
                substanceRepository.saveAndFlush(s1);
                cache.clearCache();
                ValidationResponse<Substance> response = validator.validate(s1, null);
                boolean found = response.getValidationMessages().stream().
                        anyMatch(vm->vm.getMessageType().toString().equals("WARNING") && vm.getMessage().contains(validator.getDUPLICATE_IN_SAME_RECORD_MESSAGE_TEST_FRAGMENT()));

                Assertions.assertTrue(found);
        }

        @Test
        public void testDuplicateInSameRecordShowError() {
                boolean showError = true;
                StandardNameDuplicateValidator validator = new StandardNameDuplicateValidator();
                validator.setCheckDuplicateInSameRecord(true);
//                validator.setCheckDuplicateInOtherRecord(false);
                validator.setOnDuplicateInSameRecordShowError(showError);
                validator.setSubstanceRepository(substanceRepository);
                validator.setTransactionManager(transactionManager);
                validator.setSearchService(searchService);
                validator.setCache(cache);
                Substance s1 = new Substance();
                Name name1 = new Name();
                name1.name ="TestSame1";
                name1.stdName ="TestSame1 std";
                if (name1.languages == null) {
                        name1.languages = new EmbeddedKeywordList();
                }
                name1.languages.add(new Keyword("en"));
                name1.languages.add(new Keyword("fr"));
                s1.names.add(name1);
                Name name2 = new Name();
                name2.name ="TestSame2";
                name2.stdName ="TestSame1 std";  // 1 on purpose
                if (name1.languages == null) {
                        name1.languages = new EmbeddedKeywordList();
                }
                name2.languages.add(new Keyword("en"));
                name2.languages.add(new Keyword("fr"));
                s1.names.add(name2);
                substanceRepository.saveAndFlush(s1);
                cache.clearCache();
                ValidationResponse<Substance> response = validator.validate(s1, null);
                boolean found = response.getValidationMessages().stream().
                anyMatch(vm->vm.getMessageType().toString().equals("ERROR") && vm.getMessage().contains(validator.getDUPLICATE_IN_SAME_RECORD_MESSAGE_TEST_FRAGMENT()));

                Assertions.assertTrue(found);
        }



        }
