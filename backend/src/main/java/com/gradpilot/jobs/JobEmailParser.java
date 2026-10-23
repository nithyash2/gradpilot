package com.gradpilot.jobs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Reads job application emails: confirmations, assessments, interview
 * invitations, offers and rejections from SEEK, LinkedIn, Indeed and
 * company careers sites.
 *
 * <p>It works with plain rules rather than a language model, so it is free,
 * fast, predictable and easy to test. Each rule is checked one sentence at a
 * time, and sentences that only describe what might happen ("if you have not
 * heard from us, assume you have not been successful") are skipped, because
 * Australian confirmation emails very often contain them.
 */
@Component
public class JobEmailParser {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    /** Job alerts and newsletters mention applying but are not about an application. */
    private static final List<Pattern> NOT_AN_APPLICATION = patterns(
            "jobs? (?:you may|you might|that may|we think you(?:'ll| will)) (?:be interested|like)",
            "new jobs? (?:for you|matching|that match)",
            "job alert",
            "recommended jobs",
            "jobs? based on your (?:profile|preferences|search)",
            "is hiring\\b.*\\bapply now",
            "people (?:are )?viewed your profile",
            "complete your profile");

    private static final List<Pattern> APPLIED = patterns(
            "thank(?:s| you) for (?:your )?(?:applying|application|applications|submitting your application)",
            "application (?:for [^.!?\\n]{2,80} )?(?:has been |was )?(?:received|submitted|lodged|sent)",
            "(?:we(?:'ve| have)|has been) received (?:your|an) application",
            "your application (?:was|has been|is) (?:sent|submitted|received|lodged|on its way)",
            "successfully (?:applied|submitted)",
            "you(?:'ve| have)? applied (?:for|to)",
            "application confirmation",
            "confirm(?:ing)? (?:that )?(?:we(?:'ve| have) received )?your application");

    private static final List<Pattern> ASSESSMENT = patterns(
            "(?:online|coding|technical|video|psychometric|cognitive|skills?) (?:assessment|test|challenge|exercise)",
            "hackerrank|codility|hirevue|testgorilla|codesignal|pymetrics|criteria corp|revelian|plum\\.io",
            "complete (?:the|an|our|a|this) (?:short |brief |online )?(?:assessment|test|task|challenge|questionnaire)",
            "next (?:step|stage) (?:is|will be|in the process is) (?:a|an|to complete) (?:\\w+ )?(?:assessment|test|task)");

    private static final List<Pattern> INTERVIEW = patterns(
            "invit(?:e|ed|ing) you (?:to|for) (?:an? |the )?(?:\\w+ ){0,2}(?:interview|conversation|chat|call)",
            "interview (?:invitation|invite|request|confirmation|details)",
            "(?:schedule|book|arrange|set up|organise|organize) (?:an? |your |the )?(?:\\w+ ){0,2}(?:interview|phone screen|screening call)",
            "like to (?:speak|chat|talk|meet) with you",
            "progress(?:ed|ing)? (?:you |your application )?to (?:the )?(?:next|interview) (?:stage|round)",
            "shortlisted for (?:an? |the )?(?:\\w+ )?interview",
            "phone screen");

    private static final List<Pattern> OFFER = patterns(
            "pleased to (?:offer|extend)",
            "offer of employment",
            "(?:job|employment|formal|verbal) offer",
            "letter of offer|offer letter",
            "(?:like|delighted|happy) to offer you",
            "congratulations[^.]{0,60}\\boffer");

    private static final List<Pattern> REJECTED = patterns(
            "regret to (?:inform|advise|let you know)",
            "not (?:been |were |was )?(?:successful|selected|shortlisted)",
            "(?:will|would) not be (?:progressing|proceeding|moving forward|taking)",
            "(?:won't|unable to) (?:be )?(?:progress|proceed|move forward|take)",
            "decided (?:not )?to (?:move forward|proceed|progress|continue|go ahead) with (?:other|another|a different)",
            "(?:pursue|pursuing|progress|progressing|proceed|proceeding) with other (?:candidates|applicants)",
            "other candidates (?:whose|who)",
            "(?:position|role|vacancy) has (?:now )?been filled",
            "no longer (?:being )?(?:considered|under consideration)",
            "not (?:to )?(?:progress|proceed|move forward) (?:with )?your application");

    /** "Unfortunately" on its own is a weak sign: confirmations use it too. */
    private static final Pattern WEAK_REJECTION = Pattern.compile("\\bunfortunately\\b", FLAGS);

    /** Sentences about what might happen, not what did. */
    private static final Pattern CONDITIONAL = Pattern.compile(
            "\\bif\\b|\\bshould you\\b|\\bunless\\b|\\bassume\\b|\\bonly (?:those )?shortlisted\\b"
                    + "|\\bmay be\\b|\\bmight\\b|\\bin the event\\b|\\bwhether\\b|\\bonce we\\b",
            FLAGS);

    /** Rules that find the employer and the job title, best first. */
    private static final List<Pattern> ROLE_AND_COMPANY = patterns(
            "(?:application|applying|applied|apply) (?:for|to) (?:the )?(?:position of |role of |job of )?"
                    + "(?<role>[^.,!?\\n:;]{2,80}?)(?: role| position| job| opportunity)? (?:at|with) "
                    + "(?<company>[^.,!?\\n:;()]{2,60})",
            "(?:interest in|interested in) (?:the )?(?<role>[^.,!?\\n:;]{2,80}?)(?: role| position| job| opportunity)?"
                    + " (?:at|with) (?<company>[^.,!?\\n:;()]{2,60})",
            "(?:application|applying|applied) (?:for|to) (?:the )?(?<role>[^.,!?\\n:;]{2,80}?) (?:role|position|job)"
                    + " (?:here )?at (?<company>[^.,!?\\n:;()]{2,60})");

    private static final List<Pattern> COMPANY_ONLY = patterns(
            "(?:was|has been) (?:sent|submitted) to (?<company>[^.,!?\\n:;()]{2,60})",
            "(?:thank(?:s| you) for (?:applying|your application|your interest)|applying|application|interest)"
                    + " (?:to|with|in (?:joining|working (?:at|for|with))) (?<company>[^.,!?\\n:;()]{2,60})",
            "(?:career|careers|opportunities|role|position|team) at (?<company>[^.,!?\\n:;()]{2,60})",
            "on behalf of (?:the )?(?<company>[^.,!?\\n:;()]{2,60}?)(?: team| recruitment| talent)",
            "(?:the )?(?<company>[^.,!?\\n:;()]{2,40}?) (?:recruitment|talent acquisition|careers|hiring) team");

    private static final List<Pattern> ROLE_ONLY = patterns(
            "(?:position|role|job title|vacancy)(?: of| for|:| -)\\s+(?:the )?(?<role>[^.,!?\\n:;()]{2,80})",
            "(?:application|applying|applied) for (?:the )?(?<role>[^.,!?\\n:;()]{2,80}?)(?: role| position| job)?(?=[.,!?\\n:;]| was\\b| has\\b| is\\b| at\\b| with\\b|$)",
            "^indeed application:\\s*(?<role>[^\\n]{2,80})",
            "^(?:application (?:received|confirmation|submitted)|thank you for applying)\\s*[-:|–]\\s*(?<role>[^\\n|]{2,80})");

    /** Words that stop a company or role phrase. */
    private static final Pattern TRAILING_JUNK = Pattern.compile(
            "\\s+(?:and|we|via|through|on|in|for|has|have|is|are|was|were|will|would|today|recently|"
                    + "role|position|job|team|recruitment|careers|talent acquisition)\\b.*$",
            FLAGS);

    private static final Set<String> NOT_A_COMPANY = Set.of(
            "us", "our team", "the team", "we", "you", "linkedin", "seek", "indeed", "this", "the company",
            "our company", "the role", "this role", "this position", "the position", "an", "a");

    private static final Map<String, String> JOB_BOARDS = Map.of(
            "seek.com.au", "SEEK",
            "seek.co.nz", "SEEK",
            "s.seek.com.au", "SEEK",
            "linkedin.com", "LinkedIn",
            "indeed.com", "Indeed",
            "indeedemail.com", "Indeed",
            "jora.com", "Jora",
            "gradconnection.com", "GradConnection",
            "prosple.com", "Prosple",
            "glassdoor.com", "Glassdoor");

    /** Applicant tracking systems that companies use for their own careers sites. */
    private static final Set<String> CAREERS_SYSTEMS = Set.of(
            "myworkday.com", "workday.com", "greenhouse.io", "greenhouse-mail.io", "lever.co", "hire.lever.co",
            "smartrecruiters.com", "pageuppeople.com", "pageup.com.au", "jobadder.com", "workable.com",
            "workablemail.com", "ashbyhq.com", "successfactors.com", "sapsf.com", "taleo.net", "icims.com",
            "bamboohr.com", "teamtailor.com", "recruitee.com", "livehire.com", "applytojob.com",
            "breezy.hr", "jobvite.com", "oraclecloud.com", "elmotalent.com.au", "scouttalent.com.au");

    private static final Set<String> PERSONAL_MAIL = Set.of(
            "gmail.com", "outlook.com", "hotmail.com", "live.com", "yahoo.com", "icloud.com", "bigpond.com");

    private static final Set<String> SECOND_LEVEL_SUFFIXES = Set.of(
            "com.au", "net.au", "org.au", "edu.au", "gov.au", "co.nz", "co.uk", "org.uk", "co.in", "com.sg");

    private static final Pattern SENDER_NOISE = Pattern.compile(
            "\\b(?:careers?|recruit(?:ment|ing|er)?|talent(?: acquisition)?|team|hr|jobs?|hiring|people|"
                    + "no-?reply|do-?not-?reply|notifications?|mail(?:er)?|via \\w+|@ ?\\w+)\\b",
            FLAGS);

    /** Reads an email. Empty means it is not about a job application. */
    public Optional<JobEmailFinding> parse(JobEmail email) {
        String subject = clean(email.subject());
        String text = clean(email.text());
        String all = subject + "\n" + text;

        if (anyMatch(NOT_AN_APPLICATION, all) && !anyMatch(APPLIED, all)) {
            return Optional.empty();
        }

        JobEmailKind kind = kind(subject, text);
        if (kind == null) {
            return Optional.empty();
        }

        String company = null;
        String role = null;
        for (String part : List.of(subject, text)) {
            for (Pattern pattern : ROLE_AND_COMPANY) {
                Matcher m = pattern.matcher(part);
                while (m.find() && (company == null || role == null)) {
                    String c = company(m.group("company"));
                    String r = role(m.group("role"));
                    if (c != null && r != null) {
                        company = c;
                        role = r;
                    }
                }
            }
        }
        for (String part : List.of(subject, text)) {
            company = company != null ? company : firstGroup(COMPANY_ONLY, part, "company", JobEmailParser::company);
            role = role != null ? role : firstGroup(ROLE_ONLY, part, "role", JobEmailParser::role);
        }

        String domain = domain(email.fromAddress());
        if (company == null) {
            company = companyFromSender(email.fromName(), email.fromAddress(), domain);
        }

        return Optional.of(new JobEmailFinding(kind, company, role, source(domain, all)));
    }

    /** The strongest thing the email says, checked one sentence at a time. */
    static JobEmailKind kind(String subject, String text) {
        List<String> sentences = new ArrayList<>();
        sentences.add(subject);
        for (String sentence : text.split("(?<=[.!?])\\s+|\\n+")) {
            if (!sentence.isBlank()) {
                sentences.add(sentence);
            }
        }

        boolean applied = false;
        boolean assessment = false;
        boolean interview = false;
        boolean offer = false;
        boolean rejected = false;
        boolean weakRejection = false;

        for (String sentence : sentences) {
            applied |= anyMatch(APPLIED, sentence);
            boolean conditional = CONDITIONAL.matcher(sentence).find();
            if (conditional) {
                continue;
            }
            assessment |= anyMatch(ASSESSMENT, sentence);
            interview |= anyMatch(INTERVIEW, sentence);
            offer |= anyMatch(OFFER, sentence);
            rejected |= anyMatch(REJECTED, sentence);
            weakRejection |= WEAK_REJECTION.matcher(sentence).find();
        }

        if (rejected) {
            return JobEmailKind.REJECTED;
        }
        if (offer) {
            return JobEmailKind.OFFER;
        }
        if (interview) {
            return JobEmailKind.INTERVIEW;
        }
        if (assessment) {
            return JobEmailKind.ASSESSMENT;
        }
        if (applied) {
            return JobEmailKind.APPLIED;
        }
        boolean aboutApplication = Pattern.compile("\\b(?:application|applied|candidate|candidacy)\\b", FLAGS)
                .matcher(String.join(" ", sentences)).find();
        return weakRejection && aboutApplication ? JobEmailKind.REJECTED : null;
    }

    static String source(String domain, String text) {
        if (domain != null) {
            for (Map.Entry<String, String> board : JOB_BOARDS.entrySet()) {
                if (domain.equals(board.getKey()) || domain.endsWith("." + board.getKey())) {
                    return board.getValue();
                }
            }
            if (isCareersSystem(domain)) {
                return "Company site";
            }
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("via seek") || lower.contains("on seek")) {
            return "SEEK";
        }
        if (lower.contains("via linkedin") || lower.contains("linkedin easy apply")) {
            return "LinkedIn";
        }
        if (lower.contains("via indeed") || lower.contains("on indeed")) {
            return "Indeed";
        }
        if (domain != null && !PERSONAL_MAIL.contains(domain)) {
            return "Company site";
        }
        return "Email";
    }

    /** The employer from the sender, when the email text does not name it. */
    static String companyFromSender(String fromName, String fromAddress, String domain) {
        if (domain == null || isJobBoard(domain)) {
            return null;
        }
        // Workday sends from <company>@myworkday.com
        if (domain.endsWith("myworkday.com") && fromAddress != null) {
            String local = fromAddress.substring(0, fromAddress.indexOf('@'));
            if (!local.isBlank() && !local.matches("(?i)no-?reply|donotreply")) {
                return capitalise(local.replaceAll("[._-]+", " "));
            }
        }
        if (fromName != null) {
            String name = SENDER_NOISE.matcher(fromName).replaceAll(" ")
                    .replaceAll("[|@,:•-]+", " ").replaceAll("\\s+", " ").trim();
            String company = company(name);
            if (company != null && !isJobBoardName(company)) {
                return company;
            }
        }
        if (isCareersSystem(domain) || PERSONAL_MAIL.contains(domain)) {
            return null;
        }
        return capitalise(registeredName(domain));
    }

    // ---- Helpers --------------------------------------------------------

    private static String firstGroup(List<Pattern> patterns, String text, String group,
                                     java.util.function.Function<String, String> tidy) {
        for (Pattern pattern : patterns) {
            Matcher m = pattern.matcher(text);
            while (m.find()) {
                String value = tidy.apply(m.group(group));
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    static String company(String raw) {
        String value = tidy(raw);
        if (value == null) {
            return null;
        }
        value = TRAILING_JUNK.matcher(value).replaceAll("").trim();
        value = value.replaceAll("^(?:the|our)\\s+", "");
        if (value.length() < 2 || value.length() > 60 || value.split("\\s+").length > 6
                || NOT_A_COMPANY.contains(value.toLowerCase(Locale.ROOT))
                || !Character.isLetterOrDigit(value.charAt(0))) {
            return null;
        }
        return value;
    }

    static String role(String raw) {
        String value = tidy(raw);
        if (value == null) {
            return null;
        }
        value = value.replaceAll("(?i)^(?:a|an|the|your|our|this)\\s+", "")
                .replaceAll("(?i)\\s+(?:role|position|job|opportunity|vacancy)$", "")
                .trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (value.length() < 2 || value.length() > 80 || value.split("\\s+").length > 10
                || lower.matches("(?:us|you|it|this|that|them|the role|a role|this role|the position|"
                        + "this position|applying|application|your application)")
                || lower.matches("^(?:working|joining|being|a career|careers?|employment|opportunities)\\b.*")) {
            return null;
        }
        return value;
    }

    private static String tidy(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.replaceAll("[\"“”‘’*_]", "").replaceAll("\\s+", " ").trim();
        value = value.replaceAll("[\\s\\-–—|:!.]+$", "").trim();
        return value.isEmpty() ? null : value;
    }

    /** Plain text with tidy spacing, so rules see words rather than markup. */
    static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(' ', ' ')
                .replace('’', '\'')
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll(" *\\n[ \\n]*", "\n")
                .trim();
    }

    static String domain(String address) {
        if (address == null) {
            return null;
        }
        int at = address.lastIndexOf('@');
        if (at < 0 || at == address.length() - 1) {
            return null;
        }
        return address.substring(at + 1).trim().toLowerCase(Locale.ROOT).replaceAll("[>\\s]", "");
    }

    private static boolean isJobBoard(String domain) {
        return JOB_BOARDS.keySet().stream().anyMatch(d -> domain.equals(d) || domain.endsWith("." + d));
    }

    private static boolean isJobBoardName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return JOB_BOARDS.values().stream().anyMatch(v -> lower.contains(v.toLowerCase(Locale.ROOT)))
                || lower.contains("workday") || lower.contains("greenhouse");
    }

    private static boolean isCareersSystem(String domain) {
        return CAREERS_SYSTEMS.stream().anyMatch(d -> domain.equals(d) || domain.endsWith("." + d));
    }

    /** "careers.atlassian.com" becomes "atlassian"; "jobs.nab.com.au" becomes "nab". */
    static String registeredName(String domain) {
        String[] labels = domain.split("\\.");
        if (labels.length < 2) {
            return domain;
        }
        String lastTwo = labels[labels.length - 2] + "." + labels[labels.length - 1];
        int index = SECOND_LEVEL_SUFFIXES.contains(lastTwo) && labels.length >= 3
                ? labels.length - 3
                : labels.length - 2;
        return labels[index];
    }

    private static String capitalise(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() <= 3) {
            return value.toUpperCase(Locale.ROOT);
        }
        StringBuilder out = new StringBuilder();
        for (String word : value.trim().split("\\s+")) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    private static boolean anyMatch(List<Pattern> patterns, String text) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private static List<Pattern> patterns(String... regexes) {
        List<Pattern> compiled = new ArrayList<>();
        for (String regex : regexes) {
            compiled.add(Pattern.compile(regex, FLAGS | Pattern.MULTILINE));
        }
        return List.copyOf(compiled);
    }
}
