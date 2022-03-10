package ix.ginas.utils.validation.validators.tags;
import ix.ginas.models.v1.Name;
import ix.ginas.models.v1.Substance;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

@Slf4j
public class TagUtilities{

    @Data
    public static class BracketExtraction{
        private String namePart;
        private List<String> tagTerms = new ArrayList<>();
        public void addTagTerm(String tagTerm){
            this.tagTerms.add(tagTerm);
        }
    }

    public static final Pattern splitCombinedTagRegex = Pattern.compile(":");

    public static final Pattern cleanTagTermRegex = Pattern.compile("\\s{2,}");
    public static final Pattern cleanNamePartTermRegex = Pattern.compile("\\s{2,}");

    // namePart = (.+), that is followed by space, followed by repeating pattern of bracketed terms, followed by 0 or more spaces
    // results in namePart in group 1 and raw string of concatenated bracketed terms in group 2
    // allows : as a potential tag term splitter.
    public static final Pattern  bracketTermRegex2 = Pattern.compile("(.+)[ ]+((\\[[ \\-A-Za-z0-9:]+\\])+)[ ]*$");

    // used loop over and extract bracketed terms from tagsPartRaw
    public static final Pattern bracketTermRegex3 = Pattern.compile("\\[([ \\-A-Za-z0-9:]+)\\]");

    // Set to true to clean up name part during bracket extraction.
    // Maybe make this configurable. Possible that other tools would be used for cleanup.
    public static boolean cleanNamePart = true;

    public static List<String> getBracketTerms(String name) {
        Objects.requireNonNull(name, "The name parameter in method getBracketTerms must not be null.");
        List<String> list = new ArrayList<>();
        Matcher m2 = bracketTermRegex2.matcher(name);
        if(m2.find()) {
            String namePart = m2.group(1);
            String tagsPartRaw = m2.group(2);
            Matcher m3 = bracketTermRegex3.matcher(tagsPartRaw);
            while (m3.find()) {
                Arrays.asList(splitCombinedTagRegex.split(m3.group(1))).forEach((s)->{
                    list.add(cleanTagTerm(s));
                });
            }
        }
        return list;
    }

    public static List<String> getBracketTerms(Name name){
        return getBracketTerms(name.getName());
    }

    public static String cleanTagTerm(String tag) {
        return cleanTagTermRegex.matcher(tag).replaceAll(" ").trim();
    }

    public static String cleanNamePart(String namePart) {
        if (cleanNamePart) {
            return cleanNamePartTermRegex.matcher(namePart).replaceAll(" ").trim();
        }
        return namePart;
    }

    public static BracketExtraction getBracketExtraction(String name) {
        Objects.requireNonNull(name, "The name parameter in method getBracketExtraction must not be null.");
        BracketExtraction bracketExtraction = new BracketExtraction();
        Matcher m2 = bracketTermRegex2.matcher(name);
        if(m2.find()) {
            bracketExtraction.setNamePart(cleanNamePart(m2.group(1)));
            String tagsPartRaw = m2.group(2);
            Matcher m3 = bracketTermRegex3.matcher(tagsPartRaw);
            while (m3.find()) {
                Arrays.asList(splitCombinedTagRegex.split(m3.group(1))).forEach((s)->{
                    bracketExtraction.addTagTerm(cleanTagTerm(s));
                });
            }
        }
        return bracketExtraction;
    }

    public static Set<String> extractExplicitTags(Substance s){
        return s.tags
                .stream()
                .map(ss->ss.getValue())
                .collect(Collectors.toSet());
    }

    public static Set<String> extractBracketNameTags(Substance s){
        return s.getAllNames().stream()
            .map(nn->getBracketTerms(nn))
            .flatMap(List::stream).filter(s1 ->!s1.isEmpty())
            .collect(Collectors.toSet());
    }

    public static Set<String> getSetAExcludesB(Set<String> setA, Set<String> setB){
        LinkedHashSet<String> newSet = new LinkedHashSet<>();
        newSet.addAll(setA);
        newSet.removeAll(setB);
        return newSet;
    }

    public static List<String> sortTagsHashSet(Set<String> tags){
        List<String> sorted = new ArrayList<>(tags);
        Collections.sort(sorted);
        return sorted;
    }
}

// OLD version 1, Assumes there is at most one tag term per name, and it's at the end of the name.
// public final static Pattern bracketTermRegex = Pattern.compile("\\[([^\\[\\]]*)\\]\\s*$");
// Assumes: there can be more than one tag.
// Usually these will be at the end. However, this regex will find ones not at the end.
// Can be problematic because chemical names can have brackets.
// The regex should be used in a loop to create a list of tags.
// The (?: group ) means find the group but don't remember it.
// Here we look for a [TAG] preceded by a space or a closing bracket
// But forget the preceding group (space or closing bracket).

// Old version 2
// public final static Pattern bracketTermRegex = Pattern.compile("(?:[ \\]])\\[([ \\-A-Za-z0-9]+)\\]");


/*
The reason is that we also need to detect these terms as part of name standardization to avoid changing the [ that are part of a tag to (
So the name standardizer could standardize only the namePart part, and then re-add any tagTerms
Input	Expected 1	Expected 2
ibuprofen [INN]	INN
ibuprofen[INN]	<nothing>
ibuprofen [INN][USAN]	INN	USAN
ibuprofen[INN][USAN]	<nothing>
1,2-dimethyl[something-or-other]	<nothing>
ibuprofen [WHO-DD]	WHO-DD
1,2-dimethyl[something-or-other] [INN]	INN
*/

