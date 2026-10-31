package uk.co.traynor.privategallery.core.evidence;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.*;

/** Read-only hints from untrusted OCR. Never executes instructions or silently infers a date/zone. */
public final class EvidenceTextHints {
    private EvidenceTextHints() {}
    public record Hint(String kind,String text,long epochMillis,String provenance) {}
    private record Span(int start,int end) {}
    private static final int MAX_TEXT=100_000, MAX_HINTS=256;
    private static final Pattern ISO=Pattern.compile("(?<![\\p{L}\\d])\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d{1,9})?)?(?:Z|[+-]\\d{2}:\\d{2})?(?!\\d)");
    private static final Pattern DATE=Pattern.compile("(?<!\\d)(?:\\d{1,2}/\\d{1,2}/\\d{2,4}|\\d{4}-\\d{2}-\\d{2}|\\d{1,2}\\s+(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)(?:\\s+\\d{4})?)(?!\\d)",Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME=Pattern.compile("(?<![\\d:])(?:[01]?\\d|2[0-3]):[0-5]\\d(?:\\s*[AaPp][Mm])?(?![\\d:])");
    private static final Pattern RELATIVE=Pattern.compile("\\b(?:Yesterday|Today|Tomorrow)\\b",Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME=Pattern.compile("(?m)^[ \\t]*([\\p{L}][\\p{L}\\p{M} .'-]{0,59}):[ \\t]+[^\\r\\n]");
    public static List<Hint> scan(String text) {
        Objects.requireNonNull(text);if(text.length()>MAX_TEXT)throw new IllegalArgumentException("OCR text exceeds hint limit");
        var result=new ArrayList<Hint>();var seen=new HashSet<String>();var timestamps=new ArrayList<Span>();
        Matcher iso=ISO.matcher(text);
        while(iso.find()&&result.size()<MAX_HINTS) {
            String raw=iso.group();long epoch=0;String uncertainty="timezone/precision unconfirmed";
            try {epoch=OffsetDateTime.parse(raw.replace(' ','T')).toInstant().toEpochMilli();uncertainty="explicit offset in extracted text, content unverified";}catch(DateTimeParseException ignored){}
            add(result,seen,new Hint("TIMESTAMP",raw,epoch,"OCR_VISIBLE_UNVERIFIED · "+uncertainty));timestamps.add(new Span(iso.start(),iso.end()));
        }
        append(text,DATE,"DATE","date only; time/zone unconfirmed",timestamps,result,seen);
        append(text,TIME,"TIME","clock only; date/zone unconfirmed",timestamps,result,seen);
        append(text,RELATIVE,"RELATIVE_DATE","relative date anchor unconfirmed; not the current device date",List.of(),result,seen);
        Matcher names=NAME.matcher(text);
        while(names.find()&&result.size()<MAX_HINTS)add(result,seen,new Hint("PARTICIPANT_CANDIDATE",names.group(1).trim(),0,"OCR_VISIBLE_UNVERIFIED · possible name, unconfirmed participant"));
        return List.copyOf(result);
    }
    private static void append(String text,Pattern pattern,String kind,String uncertainty,List<Span> excluded,List<Hint> out,Set<String> seen) {
        Matcher matcher=pattern.matcher(text);
        while(matcher.find()&&out.size()<MAX_HINTS) {
            if(excluded.stream().anyMatch(span->matcher.start()<span.end()&&matcher.end()>span.start()))continue;
            String raw=matcher.group(), detail=uncertainty;
            if(kind.equals("DATE")&&raw.contains("/"))detail="numeric locale/date ordering ambiguous; time/zone unconfirmed";
            add(out,seen,new Hint(kind,raw,0,"OCR_VISIBLE_UNVERIFIED · "+detail));
        }
    }
    private static void add(List<Hint> out,Set<String> seen,Hint hint) {
        if(out.size()<MAX_HINTS&&seen.add(hint.kind()+"\u0000"+hint.text()))out.add(hint);
    }
}
