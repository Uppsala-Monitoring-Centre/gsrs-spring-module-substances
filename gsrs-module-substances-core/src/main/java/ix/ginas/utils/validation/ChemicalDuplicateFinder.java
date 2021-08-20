package ix.ginas.utils.validation;

import gsrs.module.substance.repository.ChemicalSubstanceRepository;
import gsrs.module.substance.repository.KeywordRepository;
import gsrs.module.substance.repository.SubstanceRepository;
import gsrs.module.substance.repository.ValueRepository;
import ix.core.models.Keyword;
import ix.core.models.Value;
import ix.ginas.models.v1.ChemicalSubstance;
import ix.ginas.models.v1.GinasChemicalStructure;
import ix.ginas.models.v1.Substance;
import ix.ginas.models.v1.SubstanceReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Finds Chemical Structure Duplicates by looking at the hash property Keywords
 * that were generated by the Structure Processor.
 * Note: unlike the GSRS 2.x code for finding duplicates
 * this implementation will filter out the query substance we are searching
 * so there is no need to do a check to see if we got back the Substance
 * we passed in.
 */
@Component
public class ChemicalDuplicateFinder implements DuplicateFinder<SubstanceReference> {

    @Autowired
    private SubstanceRepository substanceRepository;

    @Autowired
    private ChemicalSubstanceRepository chemicalSubstanceRepository;

    @Autowired
    private KeywordRepository keywordRepository;

//    @Autowired
    @PersistenceContext(unitName =  "defaultEntityManager")
    private EntityManager entityManager;
    /**
     * Currently uses the structure.properties.term keys for the duplicate matching
     */
    @Override
    public List<SubstanceReference> findPossibleDuplicatesFor(SubstanceReference subRef) {
        int max=10;

        Map<UUID, SubstanceReference> dupMap = new LinkedHashMap<>();
        Substance sub = subRef.wrappedSubstance;
        if(sub ==null){
            sub = substanceRepository.findBySubstanceReference(subRef);
        }
        if(sub instanceof ChemicalSubstance) {
            //Hibernate/ Spring JPA repository queries could not easily do the
            //query like the GSRS 2.x with Play could where we looked for a String matching
            // structure.properties.term = $hash
            // because the properties was a Value superclass and term is only a Keyword and I could not
            // get it to downcast inside a collection.
            //
            //so instead I do a Keyword sarch to find all Keyword object that have that Term
            //and then I do a JPA query to find all the structure properties that contain that Keyword object.
            ChemicalSubstance cs = (ChemicalSubstance) sub;
            GinasChemicalStructure structure = cs.getStructure();

            String hash = structure.getStereoInsensitiveHash();

            List<Keyword> keywords = keywordRepository.findByTerm(hash);


            if (!keywords.isEmpty()) {
                Substance ourSubstance = sub;
                Predicate<ChemicalSubstance> skipOurselvesFilter = s-> !(ourSubstance.getUuid().equals(s.getUuid()));
                dupMap =
                        chemicalSubstanceRepository.findByStructure_PropertiesIn(keywords)
                                .stream()
                                .filter(skipOurselvesFilter)
                                .limit(max)

                                .collect(Collectors.toMap(Substance::getUuid, Substance::asSubstanceReference, (x, y) -> y, LinkedHashMap::new));

                if (dupMap.size() < max) {

                    dupMap.putAll(
                            chemicalSubstanceRepository.findByMoieties_Structure_PropertiesIn(keywords)
                                    .stream()
                                    .filter(skipOurselvesFilter)
                                    .limit(max - dupMap.size())
                                    .collect(Collectors.toMap(Substance::getUuid, Substance::asSubstanceReference, (x, y) -> y, LinkedHashMap::new)));

                }
            }
        }
        
        return dupMap.values().stream()

                                .collect(Collectors.toList());
    }
    
    public static ChemicalDuplicateFinder instance(){
        return new ChemicalDuplicateFinder();
    }

}
