package ix.core.chem;

import gov.nih.ncats.molwitch.Chemical;
import gov.nih.ncats.molwitch.inchi.Inchi;
import gov.nih.ncats.molwitch.io.ChemFormat;
import ix.core.models.Structure;
import ix.core.models.Text;
import ix.core.models.Value;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
public class InchiStandardizer extends AbstractStructureStandardizer {
    private static final int ATOM_LIMIT_FOR_STANDARDIZATION = 240;

    private static final ChemFormat.SmilesFormatWriterSpecification CANONICAL_SMILES_SPEC = new ChemFormat.SmilesFormatWriterSpecification()
                                                                                                    .setCanonization(ChemFormat.SmilesFormatWriterSpecification.CanonicalizationEncoding.CANONICAL);
    private final int maxNumberOfAtoms;

    public  InchiStandardizer(){
        this(ATOM_LIMIT_FOR_STANDARDIZATION);
    }

    public InchiStandardizer(int maxNumberOfAtoms){
        this.maxNumberOfAtoms = maxNumberOfAtoms;
    }
    
    @Override
    public String canonicalSmiles(Structure s, String mol) {
        String smiles=null;
        for(Value v : s.properties){
            if(Structure.F_SMILES.equals(v.label)){
                smiles = (String) v.getValue();
                break;
            }
        }
        if(smiles !=null){
            return smiles;
        }
        try {
            return s.toChemical().toSmiles(CANONICAL_SMILES_SPEC);
        } catch (Exception e) {
            //something happend when converting to chemical fallback to passed in mol?
            try{
               return Chemical.parseMol(mol).toSmiles(CANONICAL_SMILES_SPEC);
            }catch (Exception e2) {
                return null;
            }
        }
    }

    @Override
    public Chemical standardize(Chemical orig, Supplier<String> molSupplier, Consumer<Value> valueConsumer) throws IOException {

//        if(true){
//            return orig;
//        }
        if(orig.getAtomCount() > maxNumberOfAtoms || orig.hasPseudoAtoms()){
            return orig;
        }
        try {
            String inchi = orig.toInchi().getInchi();
            
            Chemical chem= Inchi.toChemical(inchi);
            //some inchi->chemical flavors have very bad clean functions and don't compute stereo or coords correctly
            //which can lead to wrong molecules so do a double check that we get the right inchi back
            if(!chem.toInchi().getInchi().equals(inchi)){
                return orig;
            }
            valueConsumer.accept(
                    (new Text(Structure.F_SMILES,chem.toSmiles(CANONICAL_SMILES_SPEC)
                           )));

            return chem;
        }catch(Exception e){
        	log.warn("Trouble using InchIStandardizer on record [" + orig.getFormula() + "] :" + e.getMessage() + " enable TRACE log level for more information");
        	if(log.isTraceEnabled()) {
        		log.trace("Trouble using InchIStandardizer on record with structure name \"" + orig.getName() + "\" [" + orig.getFormula() + "]", e);
        	}
            return orig;
        }
    }


}
