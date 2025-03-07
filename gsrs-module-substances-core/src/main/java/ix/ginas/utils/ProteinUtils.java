package ix.ginas.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nih.ncats.common.Tuple;
import gov.nih.ncats.common.util.CachedSupplier;
import gov.nih.ncats.common.util.SingleThreadCounter;
import gsrs.module.substance.repository.SubstanceRepository;
import gsrs.module.substance.utils.MolWeightCalculatorProperties;
import ix.core.chem.FormulaInfo;

import ix.core.validator.GinasProcessingMessage;
import ix.ginas.models.v1.*;
import ix.ginas.models.v1.Substance.SubstanceClass;
import ix.utils.Util;
import lombok.extern.slf4j.Slf4j;
import org.jcvi.jillion.core.residue.aa.AminoAcid;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class ProteinUtils
{

    @Autowired(required = true)
    private MolWeightCalculatorProperties molWeightCalculatorProperties;

    //Based on analysis from existing MAB entries
    private static final CachedSupplier<Map<String, List<int[]>>> KNOWN_DISULFIDE_PATTERNS = CachedSupplier.of(() -> {
        Map<String, List<int[]>> dstypes = Arrays.stream(("IGG4	0-1,11-12,13-31,14-15,18-19,2-26,20-21,22-23,24-25,27-28,29-30,3-4,5-16,6-17,7-8,9-10\n"
                + "IGG2	0-1,11-12,13-14,15-35,16-17,2-30,22-23,24-25,26-27,28-29,3-4,31-32,33-34,5-18,6-19,7-20,8-21,9-10\n"
                + "IGG1	0-1,11-12,13-14,15-31,18-19,2-3,20-21,22-23,24-25,27-28,29-30,4-26,5-16,6-17,7-8,9-10")
                .split("\n"))
                .map(s -> s.split("\t"))
                .map(s -> Tuple.of(s[0], s[1]))
                .map(Tuple.vmap(v -> v.split(",")))
                .map(Tuple.vmap(v -> Arrays.stream(v)
                .map(ds -> {
                    String[] idx = ds.split("-");
                    return new int[]{Integer.parseInt(idx[0]), Integer.parseInt(idx[1])};
                })
                .collect(Collectors.toList())
                ))
                .collect(Tuple.toMap());
        return dstypes;
    });

    //from
    //http://www.seas.upenn.edu/~cis535/Fall2004/HW/GCB535HW6b.pdf
    //switched N and D 1 December 2020 MAM, based on discussions with TP
    // the U Penn PDF is 404.
    private static String lookup
            = "A	71.09\n"
            + "R	156.19\n"
            + "D	115.09\n"
            + "N	114.11\n"
            + "C	103.15\n"
            + "E	129.12\n"
            + "Q	128.14\n"
            + "G	57.05\n"
            + "H	137.14\n"
            + "I	113.16\n"
            + "L	113.16\n"
            + "K	128.17\n"
            + "M	131.19\n"
            + "F	147.18\n"
            + "P	97.12\n"
            + "S	87.08\n"
            + "T	101.11\n"
            + "W	186.12\n"
            + "Y	163.18\n"
            + "V	99.14";

    private static String newLookup = "G	GLYCINE	57.052;A	ALANINE	71.079;S	SERINE	87.078;P	PROLINE	97.117;V	VALINE	99.133;T	THREONINE	101.105;C	CYSTEINE	103.139;I	ISOLEUCINE	113.160;L	LEUCINE	113.160;D	ASPARAGINE	115.088;N	ASPARTIC ACID	114.104;Q	GLUTAMINE	128.131;K	LYSINE	128.175;E	GLUTAMICACID	129.115;M	METHIONINE	131.193;H	HISTIDINE	137.142;F	PHENYLALANINE	147.177;R	ARGININE	156.189;Y	TYROSINE	163.176;W	TRYPTOPHAN	186.214";

    //	https://en.wikipedia.org/wiki/Amino_acid
    private static String lookupFormula
            = "A	C-3;H-7;N-1;O-2\n"
            + "R	C-6;H-14;N-4;O-2\n"
            + "D	C-4;H-7;N-1;O-4\n"
            + "N	C-4;H-8;N-2;O-3\n"  //correct H coefficient 31 July 2023
            + "C	C-3;H-7;N;O-2;S-1\n"
            + "E	C-5;H-9;N;O-4\n"
            + "Q	C-5;H-10;N-2;O-3\n"
            + "G	C-2;H-5;N;O-2\n"
            + "H	C-6;H-9;N-3;O-2\n"
            + "I	C-6;H-13;N;O-2\n"
            + "L	C-6;H-13;N;O-2\n"
            + "K	C-6;H-14;N-2;O-2\n"
            + "M	C-5;H-11;N;O-2;S\n"
            + "F	C-9;H-11;N;O-2\n"
            + "P	C-5;H-9;N;O-2\n"
            + "S	C-3;H-7;N;O-3\n"
            + "T	C-4;H-9;N;O-3\n"
            + "W	C-11;H-12;N-2;O-2\n"
            + "Y	C-9;H-11;N;O-3\n"
            + "V	C-5;H-11;N;O-2";

    private final static String MOLECULAR_FORMULA_PROPERTY_NAME = "Molecular Formula";

    static Map<String, Double> weights = new HashMap<String, Double>();
    static Map<String, Double> newWeights = new HashMap<>();

    static Map<String, Map<String, SingleThreadCounter>> atomCounts = new HashMap<>();

    private final static double DOUBLE_MATCH_CUTOFF = 0.001;
    private final static double HYDROGEN_MOLECULAR_WEIGHT = 2.016;
    //katzelda 2021: moved to MolWeightCalculatorProperties class

//  private final static String ROUND_MODE = ConfigHelper.getOrDefault("ix.gsrs.number.round.mode", "sigfigs");
//  private final static int ROUND_DIGITS = ConfigHelper.getInt("ix.gsrs.number.round.digits", 3);
    static {
        for (String line : lookup.split("\n")) {
            String[] cols = line.split("\t");
            weights.put(cols[0], Double.parseDouble(cols[1]));
        }

        for (String line : newLookup.split(";")) {
            String[] cols = line.split("\t");
            newWeights.put(cols[0], Double.parseDouble(cols[2]));
        }

        for (String line : lookupFormula.split("\n")) {
            String[] cols = line.split("\t");
            String aminoAcidAbbrev = cols[0];
            Map<String, SingleThreadCounter> elementData = new HashMap<>();
            for (String atomData : cols[1].split("\\;")) {
                String[] atomDataParts = atomData.split("\\-");
                long count = 1;
                if (atomDataParts.length > 1 && atomDataParts[1] != null) {
                    count = Long.parseLong(atomDataParts[1]);
                }
                elementData.put(atomDataParts[0], new SingleThreadCounter(count));
            }
            //remove one water
            removeWater(elementData);
            atomCounts.put(aminoAcidAbbrev, elementData);
        }
    }

    public static double getSingleAAWeight(String c) {
        Double d = newWeights.get(c.toUpperCase());
        if (d == null) {
            return 0;
        }
        return d;
    }

    public static Map<String, SingleThreadCounter> getSingleAAFormula(String c) {
        return atomCounts.getOrDefault(c.toUpperCase(), Collections.emptyMap());
    }

    public static double getSubunitWeight(Subunit sub, Set<String> unknownRes) {
        //start with extra water for end groups
        if (unknownRes == null) {
            unknownRes = new LinkedHashSet<String>();
        }
        if (sub.sequence == null || sub.sequence.length() == 0) {
            return 0.0;
        }
        double total = 18.015;
        for (char c : sub.sequence.toCharArray()) {
            double w = getSingleAAWeight(c + "");
            if (w <= 0) {
                unknownRes.add(c + "");
            }
            total += w;

        }
        return total;
    }

    public static Map<String, SingleThreadCounter> getSubunitFormulaInfo(String sub, Set<String> unknownRes) {
        log.debug("starting in getSubunitFormulaInfo with sub " + sub);
        //start with extra water for end groups
        if (unknownRes == null) {
            unknownRes = new LinkedHashSet<String>();
        }
        Map<String, SingleThreadCounter> formula = new HashMap<>();
        if (sub == null || sub.length() == 0) {
            log.debug("null/blank sequence in getSubunitFormulaInfo");
            return formula;
        }
        for (char c : sub.toCharArray()) {
            Map<String, SingleThreadCounter> residueContribution = getSingleAAFormula("" + c);
            if (residueContribution == null) {
                unknownRes.add(c + "");
            }
            else {

                for (String k : residueContribution.keySet()) {
                    if (formula.containsKey(k)) {
                        //log.debug(String.format("incrementing count for %s by %d", k, residueContribution.get(k).getAsLong()));
                        formula.get(k).increment(residueContribution.get(k).getAsLong());
                    }
                    else {
                        //log.debug(String.format("new item for %s with %d", k, residueContribution.get(k).getAsLong()));
                        formula.put(k, new SingleThreadCounter(residueContribution.get(k).getAsLong()));
                    }
                }
            }
        }
        restoreEndWater(formula);
        return formula;
    }

    public static Map<String, SingleThreadCounter> restoreEndWater(Map<String, SingleThreadCounter> formula) {
        String hydrogenSymbol = "H";
        String oxygenSymbol = "O";
        if (formula != null) {
            if (formula.containsKey(oxygenSymbol)) {
                formula.get(oxygenSymbol).increment();
            }
            if (formula.containsKey(hydrogenSymbol)) {
                formula.get(hydrogenSymbol).increment().increment();
            }
        }
        return formula;
    }

    public static double generateProteinWeight(SubstanceRepository substanceRepository, ProteinSubstance ps, Set<String> unknownRes) {
        log.trace("starting in generateProteinWeight");
        double total = 0;
        List<String> handledModTypes = Arrays.asList("AMINO ACID SUBSTITION", "AMINO-ACID SUBSTITUTION", "AMINO ACID SUBSTITUION",
                "AMINO_ACID_SUBSTITUTION", "AMINO ACID SUBSTITTUION", "AMINO ACID REMOVAL", "AMINO ACID REPLACEMENT", "AMINO ACID SUBSITUTE",
                "AMINO ACID REPLACMENT", "AMINO ACID SUBSTITUTION");
        if (unknownRes == null) {
            unknownRes = new LinkedHashSet<String>();
        }
        for (Subunit su : ps.protein.subunits) {
            total += getSubunitWeight(su, unknownRes);
        }
        log.trace(String.format("basic MW: %.2f", total));
        if (ps.hasModifications() && ps.modifications.structuralModifications.size() > 0) {
            log.trace("considering structuralModifications");
            double waterMW = 18.0;//https://tripod.nih.gov/ginas/app/substances?q=water
            //double acetylGroupWt = 43.045d;//https://en.wikipedia.org/wiki/Acetyl_group
            for (StructuralModification mod
                    : ps.modifications.structuralModifications.stream()
                            .filter(m -> m.molecularFragment != null && m.molecularFragment.refuuid != null
                            && (m.extent != null && m.extent.equalsIgnoreCase("COMPLETE"))
                            && handledModTypes.contains(m.structuralModificationType)
                            && m.getSites().size() > 0)
                            .collect(Collectors.toSet())) {
                //The following information can be used to determine whether it's useful to factor in partial extents with a numeric amount
                String message
                        = String.format("mod.residueModified: %s; mod.molecularFragment.refuuid: %s, mod.molecularFragment.approvalID: %s; extent: %s, amount: %s, residue: %s, structuralModificationType: [%s]",
                                mod.residueModified, mod.molecularFragment.refuuid, mod.molecularFragment.approvalID,
                                mod.extent, (mod.extentAmount == null) ? "null" : mod.extentAmount.toString(), mod.residueModified, mod.structuralModificationType);
                log.trace(message);

                MolecularWeightAndFormulaContribution contribution = getContributionForID(substanceRepository.findById(UUID.fromString(mod.molecularFragment.refuuid)).orElse(null));
                //getContributionForID adds a warning if the molecular weight for the modification
                // can't be determined. This shouldn't be that rare of an occurence. Right now, we just ignore these case
                // but that isn't sustainable for complex substances.

                if (contribution != null) {
                    //modificationAddition= modificationAddition + contribution.getMw() + waterEffect;
                    log.trace(String.format("handling modification (structural; has fragment; has sites)retrieved contribution: %.2f from %s; total sites: %d",
                            contribution.getMw(), contribution.getSubstanceClass(), mod.getSites().size()));
                    //in the case of a substitution, we consider the mw contribution of the residue that's replaced
                    for (Site site : mod.getSites()) {
                        Subunit s = ps.protein.subunits.get((site.subunitIndex - 1));
                        char aa = s.sequence.charAt(site.residueIndex - 1);
                        String aaCode = "" + aa;
                        AminoAcid acid = AminoAcid.parse(aa);
                        double aaWt = getSingleAAWeight(aaCode);
                        double siteContribution = contribution.getMw() - waterMW - aaWt; //the value from getSingleAAWeight includes the effect of removal of 1 H2O
                        // the contribution's mw does not, so we subtract the mw of water
                        log.trace(String.format("processing site with subunit %d; residue number %d; AA: %c; name: %s; aa mw: %.2f; net effect of site: %.2f",
                                site.subunitIndex, site.residueIndex, aa, acid.getName(), aaWt, siteContribution));
                        total += siteContribution;
                    }
                }
            }
        }
        else {
            log.debug("no mods to consider");
        }

        log.trace("final total: " + total);
        return total;
    }

    public static MolecularWeightAndFormulaContribution generateProteinWeightAndFormula(SubstanceRepository substanceRepository, ProteinSubstance ps, Set<String> unknownRes) {
        log.trace("starting in generateProteinWeightAndFormula.  Total ps.protein.subunits: " + ps.protein.subunits.size());
        double total = 0.0;
        //The next 4 items are DELTAS off the total
        double lowTotal = 0.0;
        double highTotal = 0.0;
        double lowLimitTotal = 0.0;
        double highLimitTotal = 0.0;
        Map<String, SingleThreadCounter> formulaCounts = new HashMap<>();
        MolecularWeightAndFormulaContribution result = null;

        List<String> handledModTypes = Arrays.asList("AMINO ACID SUBSTITION", "AMINO-ACID SUBSTITUTION", "AMINO ACID SUBSTITUION",
                "AMINO_ACID_SUBSTITUTION", "AMINO ACID SUBSTITTUION", "AMINO ACID REMOVAL", "AMINO ACID REPLACEMENT", "AMINO ACID SUBSITUTE",
                "AMINO ACID REPLACMENT", "AMINO ACID SUBSTITUTION");
        if (unknownRes == null) {
            unknownRes = new LinkedHashSet<>();
        }
        List<GinasProcessingMessage> messages = new ArrayList<>();
        for (Subunit su : ps.protein.subunits) {
            double subunitMw = getSubunitWeight(su, unknownRes);
            log.trace(String.format("mw for subunit %d: %.4f", su.subunitIndex, subunitMw));
            total += subunitMw;
            Map<String, SingleThreadCounter> contribution = getSubunitFormulaInfo(su.sequence, unknownRes);
            contribution.keySet().forEach(k -> {
                if (formulaCounts.containsKey(k)) {
                    //log.debug(String.format("incrementing count for element %s by %d", k, contribution.get(k).getAsLong()));
                    formulaCounts.get(k).increment(contribution.get(k).getAsLong());
                }
                else {
                    formulaCounts.put(k, new SingleThreadCounter(contribution.get(k).getAsLong()));
                    //log.debug(String.format("creating count for element %s by %d", k, contribution.get(k).getAsLong()));
                }
            });
        }

        double disulfideContribution = getDisulfideContribution(ps.protein);
        log.trace(String.format("basic MW: %.2f; basic formula: %s; disulfideContribution: %.2f",
                total, makeFormulaFromMap(formulaCounts), disulfideContribution));
        total -= disulfideContribution;
        Map<String, SingleThreadCounter> formulaMapWithContrib[] =new Map[1];
        formulaMapWithContrib[0]=formulaCounts;
        if (ps.hasModifications() && ps.modifications.structuralModifications.size() > 0) {
            log.debug("considering structuralModifications");
            double waterMW = 18.02;//https://tripod.nih.gov/ginas/app/substances?q=water
            //double acetylGroupWt = 43.045d;//https://en.wikipedia.org/wiki/Acetyl_group
            for (StructuralModification mod
                    : ps.modifications.structuralModifications.stream()
                            .filter(m -> m.molecularFragment != null && m.molecularFragment.refuuid != null
                            && handledModTypes.contains(m.structuralModificationType)
                            && m.getSites().size() > 0)
                            .collect(Collectors.toSet())) {
                String message
                        = String.format("mod.residueModified: %s; mod.molecularFragment.refuuid: %s, mod.molecularFragment.approvalID: %s; extent: %s, amount: %s, residue: %s, structuralModificationType: [%s]",
                                mod.residueModified, mod.molecularFragment.refuuid, mod.molecularFragment.approvalID,
                                mod.extent, (mod.extentAmount == null) ? "null" : mod.extentAmount.toString(), mod.residueModified, mod.structuralModificationType);
                log.trace(message);

                if (mod.extent != null && mod.extent.equalsIgnoreCase("COMPLETE")) {
                    MolecularWeightAndFormulaContribution contribution = getContributionForID(substanceRepository.findById(UUID.fromString(mod.molecularFragment.refuuid)).orElse(null));
                    if (contribution == null) {
                        //There is no computable fragment to use
                        log.info("No usable molecular weight contribution from structural modification fragment: " + mod.molecularFragment.refuuid);
                        messages.add(
                                GinasProcessingMessage.WARNING_MESSAGE(
                                        String.format("Structural modification will not affect the molecular weight because %s has no associated molecular weight",
                                                mod.molecularFragment.refPname)));
                    }
                    else if (contribution.getMw() > 0 || (contribution.getMwHigh() > 0 && contribution.getMwLow() > 0)) {
                        //removeWater(contribution.getFormulaMap()); 1 December 2020 MAM no need for this
                        //modificationAddition= modificationAddition + contribution.getMw() + waterEffect;
                        log.trace(String.format("handling modification (structural; has fragment; has sites)retrieved contribution: mw: %.2f; mw high: %.2f; mw low: %.2f;from %s; total sites: %d",
                                contribution.getMw(), contribution.getMwHigh(), contribution.getMwLow(),
                                contribution.getSubstanceClass(), mod.getSites().size()));
                        //in the case of a substitution, we consider the mw contribution of the residue that's replaced
                        for (Site site : mod.getSites()) {
                            Subunit s = ps.protein.subunits.get((site.subunitIndex - 1));
                            char aa = s.sequence.charAt(site.residueIndex - 1);
                            String aaCode = "" + aa;
                            AminoAcid acid = AminoAcid.parse(aa);
                            double aaWt = getSingleAAWeight(aaCode);
                            double siteContribution = contribution.getMw() - waterMW - aaWt; //the value from getSingleAAWeight includes the effect of removal of 1 H2O
                            // the contribution's mw does not so we substract the mw of water
                            log.trace(String.format("processing site with subunit %d; residue number %d; AA: %c; name: %s; aa mw: %.2f; net effect of site: %.2f",
                                    site.subunitIndex, site.residueIndex, aa, acid.getName(), aaWt, siteContribution));
                            total += siteContribution;
                            if (contribution.getMwLow() != null && contribution.getMwLow() > 0) {
                                double lowDelta = contribution.getMw() - contribution.getMwLow();
                                lowTotal += lowDelta;
                                log.trace(String.format("lowDelta: %.2f; lowTotal: %.2f", lowDelta, lowTotal));
                            }
                            if (contribution.getMwHigh() != null && contribution.getMwHigh() > 0.0) {
                                double highDelta = contribution.getMwHigh() - contribution.getMw();
                                highTotal += highDelta;
                                log.trace(String.format("highDelta: %.2f; highTotal: %.2f", highDelta, highTotal));
                            }
                            if (contribution.getMwHighLimit() != null && contribution.getMwHighLimit() > 0) {
                                double highLimitDelta = contribution.getMwHighLimit() - contribution.getMw();
                                highLimitTotal += highLimitDelta;
                                log.trace(String.format("highLimitDelta: %.2f; highLimitTotal: %.2f", highLimitDelta, highLimitTotal));
                            }
                            if (contribution.getMwLowLimit() != null && contribution.getMwLowLimit() > 0) {
                                double lowLimitDelta = contribution.getMw() - contribution.getMwLowLimit();
                                lowLimitTotal += lowLimitDelta;
                                log.trace(String.format("lowLimitDelta: %.2f; lowLimitTotal: %.2f", lowLimitDelta, lowLimitTotal));
                            }
                            formulaMapWithContrib[0] = addFormulas(formulaMapWithContrib[0], contribution.getFormulaMap());
                            Map<String, SingleThreadCounter> aaContribution = getSingleAAFormula(aaCode);
                            formulaMapWithContrib[0]=subtractFormulas(formulaMapWithContrib[0], aaContribution);
                            removeWater(formulaMapWithContrib[0]);
                        }
                        messages.addAll(contribution.getMessages());
                    }
                    else {
                        messages.addAll(contribution.getMessages());
                    }
                }
                else {
                    log.trace("extent other than complete: " + mod.extent);
                    messages.add(GinasProcessingMessage.WARNING_MESSAGE(
                            String.format("Note: structural modification with extent '%s' will not be counted toward the molecular weight",
                                    mod.extent != null ? mod.extent : "[missing]")));
                }
            }
        }
        else {
            log.debug("no mods to consider");
        }

        log.trace(String.format("final total: %.2f; highTotal: %.2f; lowTotal: %.2f; highLimitTotal: %.2f; lowLimitTotal: %.2f", total,
                highTotal, lowTotal, highLimitTotal, lowLimitTotal));
        result = new MolecularWeightAndFormulaContribution(total, ps.substanceClass.toString(), formulaMapWithContrib[0]);
        result.setFormula(FormulaInfo.toCanonicalString(makeFormulaFromMap(formulaMapWithContrib[0])));
        result.setMessages(messages);
        result.setMwHigh(highTotal);
        result.setMwLow(lowTotal);
        result.setMwHighLimit(highLimitTotal);
        result.setMwLowLimit(lowLimitTotal);
        return result;
    }

    public static List<Property> getMolWeightProperties(ProteinSubstance ps) {
        List<Property> props = new ArrayList<Property>();
        if (ps.properties != null) {
            for (Property p : ps.properties) {
                ObjectMapper om = new ObjectMapper();
                JsonNode asJson = om.valueToTree(p);
                //System.out.println(p.type + "\t" + p.name +"\t" + p.propertyType + "\t" + p.value.average +"\t" + asJson);
                if (p.getName() != null && p.getName().startsWith("MOL_WEIGHT")) {
                    props.add(p);
                }
            }
        }
        return props;
    }

    public static List<Property> getMolFormulaProperties(ProteinSubstance ps) {
        List<Property> props = new ArrayList<Property>();
        if (ps.properties != null) {
            for (Property p : ps.properties) {
                if (p.getName() != null && p.getName().startsWith(MOLECULAR_FORMULA_PROPERTY_NAME)) {
                    props.add(p);
                }
            }
        }
        return props;
    }

    public static double roundToSignificantFigures(double num, int n) {
        if (num == 0) {
            return 0;
        }

        final double d = Math.ceil(Math.log10(num < 0 ? -num : num));
        final int power = n - (int) d;

        final double magnitude = Math.pow(10, power);
        final long shifted = Math.round(num * magnitude);
        return shifted / magnitude;
    }

    public static double roundToDecimals(double num, int n) {
        String format = String.format("%s.%df", "%", n);
        String formattedNumber = String.format(format, num);
        log.trace("formattedNumber: " + formattedNumber);
        return Double.parseDouble(formattedNumber);
    }

    //katzelda Feb 2021: moved to MolWeightCalculatorProperties
//  public static Property makeMolWeightProperty(double avg) {
//    return makeMolWeightProperty(avg, 0.0, 0.0, 0.0, 0.0);
//  }
//
//  public static Property makeMolWeightProperty(double avg, double low, double high, double lowLimit, double highLimit){
//		Property p= new Property();
//		p.setName("MOL_WEIGHT:NUMBER(CALCULATED)");
//		p.setType("amount");
//		p.setPropertyType("CHEMICAL");
//
//		Amount amt = new Amount();
//		amt.type="ESTIMATED";
//    String msg = String.format("rounding mode: %s; digits: %d",ROUND_MODE, ROUND_DIGITS);
//    log.debug(msg);
//    log.trace("avg: " + avg);
//		amt.average= ROUND_MODE.equalsIgnoreCase("sigfigs") ? roundToSignificantFigures(avg, ROUND_DIGITS) :
//            roundToDecimals(avg, ROUND_DIGITS);
//    log.debug("average: " + amt.average);
//    if( Math.abs(low)> DOUBLE_MATCH_CUTOFF ) {
//      low = avg-low;
//      log.trace(String.format("setting amount low to %.2f", low));
//      amt.low=ROUND_MODE.equalsIgnoreCase("sigfigs") ? roundToSignificantFigures(low, ROUND_DIGITS) :
//              roundToDecimals(low, ROUND_DIGITS);
//              //roundToSignificantFigures(low,3);
//    } else {
//      log.trace("low value skipped");
//    }
//    if( Math.abs(lowLimit)> DOUBLE_MATCH_CUTOFF ) {
//      lowLimit = avg-lowLimit;
//      log.trace(String.format("setting amount lowLimit to %.2f", lowLimit));
//      amt.lowLimit=ROUND_MODE.equalsIgnoreCase("sigfigs") ? roundToSignificantFigures(lowLimit, ROUND_DIGITS) :
//              roundToDecimals(lowLimit, ROUND_DIGITS);
//              //roundToSignificantFigures(low,3);
//    } else {
//      log.trace("low limit skipped");
//    }
//    if( Math.abs(high)> DOUBLE_MATCH_CUTOFF ) {
//      high = avg+high;
//      log.trace(String.format("setting amount high to %.2f", high));
//      amt.high= ROUND_MODE.equalsIgnoreCase("sigfigs") ? roundToSignificantFigures(high, ROUND_DIGITS) :
//              roundToDecimals(high, ROUND_DIGITS);
//              //roundToSignificantFigures(high,3);
//    } else {
//      log.trace("high value skipped");
//    }
//    if( Math.abs(highLimit)> DOUBLE_MATCH_CUTOFF ) {
//      log.trace(String.format("processing highLimit %.2f", highLimit));
//      highLimit = avg+highLimit;
//      log.trace(String.format("setting amount highLimit to %.2f", highLimit));
//      amt.highLimit = ROUND_MODE.equalsIgnoreCase("sigfigs") ? roundToSignificantFigures(highLimit, ROUND_DIGITS) :
//              roundToDecimals(highLimit, ROUND_DIGITS);
//    } else {
//      log.trace("high limit skipped");
//    }
//		amt.units="Da";
//		p.setValue(amt);
//
//		return p;
//	}
//
    public static Property makeMolFormulaProperty(String formula) {
        Property p = new Property();
        p.setName(MOLECULAR_FORMULA_PROPERTY_NAME);
        p.setPropertyType("CHEMICAL");

        Amount amt = new Amount();
        amt.type = "ESTIMATED";
        amt.nonNumericValue = formula;
        p.setValue(amt);

        return p;
    }

    /**
     * Return a stream of the sites for a protein, labeled by their residue
     *
     * @param su
     * @return
     */
    public static Stream<Tuple<String, Site>> extractSites(Subunit su) {
        if (su.sequence == null) {
            return Stream.empty();
        }

        return su.sequence.chars()
                .mapToObj(i -> Character.toString((char) i))
                .map(Util.toIndexedTuple())
                .map(t -> t.swap())
                .map(Tuple.vmap(i -> {
                    Site s = new Site();
                    s.residueIndex = i + 1;
                    s.subunitIndex = su.subunitIndex;
                    return s;
                }));
    }

    /**
     * Attempts to predict a list of the disulfide links based on the protein
     * subtype.
     * <p>
     * Specifically, at present, this will look at the subtype of the protein,
     * and determine if it is one of the known types. If it is, it uses the most
     * common disulfide pattern found for that subtype, based on the sequence of
     * cysteine residues found in the subunits, sorted by size, largest first.
     * So, for example, IGG1 monoclonal antibodies tend to have a disulfide
     * pattern specific to the order of cysteines, where the first C is linked to
     * the second, the third is linked to the forth, etc ...
     * </p>
     *
     * <p>
     * If the subtype does not have a known pattern, then an empty optional is
     * returned.
     * </p>
     *
     * @param p
     * @return
     */
    public static Optional<List<DisulfideLink>> predictDisulfideLinks(Protein p) {
        String subtype = p.proteinSubType;

        List<int[]> predicted = p.getProteinSubtypes().stream()
                .map(st -> KNOWN_DISULFIDE_PATTERNS.get().get(st))
                .filter(li -> li != null)
                .findFirst()
                .orElse(null);

        if (predicted == null) {
            return Optional.empty();
        }

        //First, get out the cystiene sites, in order.
        List<Tuple<Integer, Site>> cSites = p.subunits.stream()
                .sorted(Comparator.comparing(su -> -su.getLength()))
                .flatMap(su -> extractSites(su))
                .filter(t -> "C".equalsIgnoreCase(t.k()))
                .map(Util.toIndexedTuple())
                .map(Tuple.vmap(t -> t.v()))
                .collect(Collectors.toList());

        return Optional.of(predicted.stream()
                .map(s -> {
                    DisulfideLink dl = new DisulfideLink();
                    List<Site> sites = new ArrayList<Site>();
                    sites.add(cSites.get(s[0]).v());
                    sites.add(cSites.get(s[1]).v());

                    dl.setSites(sites);
                    return dl;
                })
                .collect(Collectors.toList()));
    }

    private static MolecularWeightAndFormulaContribution getContributionForID(Substance referencedSubstance) {
        if (referencedSubstance == null) {
            return null;
        }
        MolecularWeightAndFormulaContribution contribution = null;
        log.trace("Found referenced substance");
        Double mw = null;
        Double mwHigh = 0.0d;
        Double mwLow = 0.0d;
        Double mwHighLimit = 0.0d;
        Double mwLowLimit = 0.0d;
        String formula = null;
        boolean containsStarAtom = false;
        if (referencedSubstance.substanceClass.equals(SubstanceClass.chemical)) {
            ChemicalSubstance chemical = (ChemicalSubstance) referencedSubstance;
            mw = chemical.getStructure().getMwt();
            log.trace(String.format("mod substance is a chemical. mw: %,.2f", mw));
            formula = chemical.getStructure().formula;
            containsStarAtom = chemical.getStructure().molfile.contains(" * ");
        }
        else {
            //TODO: This currently only considers the average number, but that's
            // not ideal since modifications may have ranges. However,
            // propagation of errors is a concern here, and would need a more
            // robust handling.
            log.trace("other than chemical; looking at properties");
            for (Property property : referencedSubstance.properties) {
                if (property.getName() != null && property.getName().startsWith("MOL_WEIGHT") && property.getValue() != null) {
                    if (property.getValue().average != null) {
                        mw = property.getValue().average;
                        log.trace("retrieved average mw: " + mw);
                        mwHigh = property.getValue().high;
                        mwHighLimit = property.getValue().highLimit;
                        mwLow = property.getValue().low;
                        mwLowLimit = property.getValue().lowLimit;
                        log.trace("retrieved high mw: " + mwHigh + "; low mw: " + mwLow + "; highLimit mw: "
                                + mwHighLimit + "; lowLimit mw: " + mwLowLimit);
                    }
                }
            }
        }
        if (mw != null) {
            log.trace("referencedSubstance: " + referencedSubstance.substanceClass.name());
            log.trace("formula: " + formula);
            contribution = new MolecularWeightAndFormulaContribution(mw, mwHigh, mwLow, mwHighLimit, mwLowLimit,
                    referencedSubstance.substanceClass.name(), formula);
            log.trace("contribution: " + ((contribution == null) ? "null" : "not null"));
            if ((mwHigh != null && mwHigh > 0) || (mwHighLimit != null && mwHighLimit > 0)
                    || (mwLow != null && mwLow > 0) || (mwLowLimit != null && mwLowLimit > 0)) {
                contribution.getMessages().add(GinasProcessingMessage.WARNING_MESSAGE(
                        String.format("Using range of molecular weights for substance %s in structural modification",
                                referencedSubstance.getName())));
            }
        }
        else {
            GinasProcessingMessage warning = GinasProcessingMessage.WARNING_MESSAGE(
                    String.format("Structural modification molecular fragment %s has no average molecular weight and will not be used in the molecular weight calculation",
                            referencedSubstance.getApprovalIDDisplay()));
            contribution = new MolecularWeightAndFormulaContribution(referencedSubstance.substanceClass.name(), warning);
        }
        log.trace("containsStarAtom: " + containsStarAtom);
        if (containsStarAtom) {
            contribution.getMessages().add(
                    GinasProcessingMessage.WARNING_MESSAGE("Note: molecular fragment used in structural modifications contains '*' atom.  Molecular weight calculation may be off"));
        }

        return contribution;
    }

    private static void appendOneSpecies(StringBuilder formulaBuilder, Map<String, SingleThreadCounter> map, String species) {
        formulaBuilder.append(species);
        SingleThreadCounter speciesCount = map.get(species);
        if (speciesCount != null && speciesCount.getAsInt() > 1) {
            formulaBuilder.append(speciesCount.getAsInt());
        }
    }

    public static String makeFormulaFromMap(Map<String, SingleThreadCounter> map) {
        if (map.isEmpty()) {
            log.trace("empty map in makeFormulaFromMap");
            return "";
        }
        StringBuilder formula = new StringBuilder();

        if (map.containsKey("C")) {
            appendOneSpecies(formula, map, "C");
        }
        if (map.containsKey("H")) {
            appendOneSpecies(formula, map, "H");
        }

        map.keySet().stream()
                .filter(k -> !(k.equals("H") || k.equals("C")))
                .sorted()
                .forEach(k -> {
                    appendOneSpecies(formula, map, k);
                });
        return formula.toString().trim();
    }

    public static void removeWater(Map<String, SingleThreadCounter> formulaInfo) {
        if (formulaInfo.containsKey("H") && formulaInfo.containsKey("O")) {
            formulaInfo.get("H").decrement(2);
            formulaInfo.get("O").decrement(1);
        }
    }

    public static double getDisulfideContribution(Protein protein) {
        return (protein != null && protein.getDisulfideLinks() != null)
                ? protein.getDisulfideLinks().size() * HYDROGEN_MOLECULAR_WEIGHT
                : 0.0d;
    }

    public static Map<String,SingleThreadCounter> subtractFormulas(Map<String,SingleThreadCounter> minuend, Map<String,SingleThreadCounter> subtrahend){
        Map<String,SingleThreadCounter> difference = new HashMap<>();
        minuend.keySet().forEach(s->{
            int newValue =minuend.get(s).getAsInt();
            if( subtrahend.containsKey(s)){
                newValue -= subtrahend.get(s).getAsInt();
            }
            difference.put(s, new SingleThreadCounter(newValue));
        });
        //now add any keys from the second operand that have not been handled yet
        subtrahend.keySet().forEach(k->{
            if( !difference.containsKey(k)) {
                difference.put(k, new SingleThreadCounter( -1* subtrahend.get(k).getAsInt()));
            }
        });
        return difference;
    }

    public static Map<String,SingleThreadCounter> addFormulas(Map<String,SingleThreadCounter> addend1, Map<String,SingleThreadCounter> addend2){
        Map<String,SingleThreadCounter> difference = new HashMap<>();
        addend1.keySet().forEach(s->{
            int newValue =addend1.get(s).getAsInt();
            if( addend2.containsKey(s)){
                newValue += addend2.get(s).getAsInt();
            }
            difference.put(s, new SingleThreadCounter(newValue));
        });
        //now add any keys from the second operand that have not been handled yet
        addend2.keySet().forEach(k->{
            if( !difference.containsKey(k)) {
                difference.put(k, new SingleThreadCounter( addend2.get(k).getAsInt()));
            }
        });
        return difference;
    }

    //katzelda Feb 2021: this is duplicated logic of the Amount part of the Property calculation
//  public static String getAmountString(double avg, double low, double high, double lowLimit, double highLimit) {
//    log.trace("ProteinUtils.getAmountString");
//      Amount calculatedAmount = new Amount();
//      calculatedAmount.average= ROUND_MODE.equalsIgnoreCase("sigfigs") ? roundToSignificantFigures(avg, ROUND_DIGITS) :
//            roundToDecimals(avg, ROUND_DIGITS);
//
//		calculatedAmount.average= ROUND_MODE.equalsIgnoreCase("sigfigs") ? roundToSignificantFigures(avg, ROUND_DIGITS) :
//            roundToDecimals(avg, ROUND_DIGITS);
//    log.trace("average: " + calculatedAmount.average);
//    if( Math.abs(low)> DOUBLE_MATCH_CUTOFF ) {
//      low = avg-low;
//      log.trace(String.format("setting amount low to %.2f", low));
//      calculatedAmount.low=ROUND_MODE.equalsIgnoreCase("sigfigs") ? roundToSignificantFigures(low, ROUND_DIGITS) :
//              roundToDecimals(low, ROUND_DIGITS);
//              //roundToSignificantFigures(low,3);
//    } else {
//      log.trace("low value omitted from getAmountString");
//    }
//    if( Math.abs(lowLimit)> DOUBLE_MATCH_CUTOFF ) {
//      lowLimit = avg-lowLimit;
//      log.trace(String.format("setting amount lowLimit to %.2f", lowLimit));
//      calculatedAmount.lowLimit=ROUND_MODE.equalsIgnoreCase("sigfigs") ? roundToSignificantFigures(lowLimit, ROUND_DIGITS) :
//              roundToDecimals(lowLimit, ROUND_DIGITS);
//              //roundToSignificantFigures(low,3);
//    } else {
//      log.trace("low limit omitted from getAmountString");
//    }
//    if( Math.abs(high)> DOUBLE_MATCH_CUTOFF ) {
//      high = avg+high;
//      log.trace(String.format("setting amount high to %.2f", high));
//      calculatedAmount.high= ROUND_MODE.equalsIgnoreCase("sigfigs") ? roundToSignificantFigures(high, ROUND_DIGITS) :
//              roundToDecimals(high, ROUND_DIGITS);
//              //roundToSignificantFigures(high,3);
//    } else {
//      log.trace("high value omitted from getAmountString");
//    }
//    if( Math.abs(highLimit)> DOUBLE_MATCH_CUTOFF ) {
//      log.trace(String.format("processing highLimit %.2f", highLimit));
//      highLimit = avg+highLimit;
//      log.trace(String.format("setting amount highLimit to %.2f", highLimit));
//      calculatedAmount.highLimit = ROUND_MODE.equalsIgnoreCase("sigfigs") ? roundToSignificantFigures(highLimit, ROUND_DIGITS) :
//              roundToDecimals(highLimit, ROUND_DIGITS);
//    } else {
//      log.trace("high limit omitted from getAmountString");
//    }
//		calculatedAmount.units="Da";
//    return calculatedAmount.toString();
//  }

}
