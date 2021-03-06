/**
 * Copyright (c) 2012-2016 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.runtime.objects.intl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.ibm.icu.util.TimeZone;
import com.ibm.icu.util.TimeZone.SystemTimeZoneType;

/**
 * Simple tools to generate the various language data for the intl package
 */
final class IntlDataTools {
    private IntlDataTools() {
    }

    public static void main(String[] args) throws IOException {
        // Path cldrMainDir = java.nio.file.Paths.get("/tmp/cldr-2.0.0-core--main");
        // oldStyleLanguageTags(cldrMainDir);

        // Path currencyFile = java.nio.file.Paths.get("/tmp/iso_currency.xml");
        // currencyDigits(currencyFile);

        // Path tzdataDir = java.nio.file.Paths.get("/tmp/tzdata2013c.tar");
        // jdkTimezoneNames(tzdataDir);

        // Path langSubtagReg = java.nio.file.Paths.get("/tmp/language-subtag-registry.txt");
        // languageSubtagRegistry(langSubtagReg);
    }

    /**
     * {@link LanguageSubtagRegistryData}
     * 
     * @param langSubtagReg
     *            the language subtag registry file
     * @throws IOException
     *             if an I/O error occurs
     */
    static void languageSubtagRegistry(Path langSubtagReg) throws IOException {
        List<String> lines = Files.readAllLines(langSubtagReg, StandardCharsets.UTF_8);
        ArrayDeque<String> stack = new ArrayDeque<>(lines);

        ArrayList<Record> language = new ArrayList<>();
        ArrayList<Record> region = new ArrayList<>();
        ArrayList<Record> grandfathered = new ArrayList<>();
        ArrayList<Record> redundant = new ArrayList<>();

        ArrayList<Record> extlang = new ArrayList<>();
        ArrayList<Record> script = new ArrayList<>();
        ArrayList<Record> variant = new ArrayList<>();

        // skip first two lines (file date + %% separator)
        stack.pop();
        stack.pop();
        while (!stack.isEmpty()) {
            Record rec = readRecord(stack);
            String type = rec.get(Field.Type);
            assert type != null;
            if ("language".equals(type)) {
                if (rec.has(Field.PreferredValue)) {
                    language.add(rec);
                }
            }
            if ("region".equals(type)) {
                if (rec.has(Field.PreferredValue)) {
                    region.add(rec);
                }
            }
            if ("grandfathered".equals(type)) {
                grandfathered.add(rec);
            }
            if ("redundant".equals(type)) {
                if (rec.has(Field.PreferredValue)) {
                    redundant.add(rec);
                }
            }
            if ("extlang".equals(type)) {
                if (rec.has(Field.PreferredValue)) {
                    extlang.add(rec);
                }
            }
            if ("script".equals(type)) {
                if (rec.has(Field.PreferredValue)) {
                    script.add(rec);
                }
            }
            if ("variant".equals(type)) {
                if (rec.has(Field.PreferredValue)) {
                    variant.add(rec);
                }
            }
        }

        /* Generate LanguageSubtagRegistryData#scriptData entries */
        System.out.println("--- [LanguageSubtagRegistryData#scriptData] ---");
        for (Record record : script) {
            assert record.has(Field.Prefix);
            System.out.printf("%s -> %s [%s]%n", record.get(Field.Subtag), record.get(Field.PreferredValue),
                    record.get(Field.Prefix));
        }
        System.out.println();
        assert script.isEmpty() : "no preferred values for 'script' expected";

        /* Generate LanguageSubtagRegistryData#extlangData entries */
        System.out.println("--- [LanguageSubtagRegistryData#extlangData] ---");
        for (Record record : extlang) {
            assert record.has(Field.Prefix);
            assert record.get(Field.Subtag).equals(record.get(Field.PreferredValue)) : record.get(Field.Subtag);
            System.out.printf("map.put(\"%s\", \"%s\");%n", record.get(Field.Subtag), record.get(Field.Prefix));
        }
        System.out.println();

        /* Generate LanguageSubtagRegistryData#variantData entries */
        System.out.println("--- [LanguageSubtagRegistryData#variantData] ---");
        for (Record record : variant) {
            assert record.has(Field.Prefix);
            System.out.printf("%s -> %s [%s]%n", record.get(Field.Subtag), record.get(Field.PreferredValue),
                    record.get(Field.Prefix));
            System.out.printf("map.put(\"%s\", \"%s\");%n", record.get(Field.Subtag), record.get(Field.PreferredValue));
        }
        System.out.println();
        assert variant.size() == 1 : "Only one variant entry expected";
        assert variant.get(0).get(Field.Subtag).equals("heploc");
        assert variant.get(0).get(Field.PreferredValue).equals("alalc97");

        /* Generate LanguageSubtagRegistryData#regionData entries */
        System.out.println("--- [LanguageSubtagRegistryData#regionData] ---");
        for (Record record : region) {
            assert !record.has(Field.Prefix);
            System.out.printf("map.put(\"%s\", \"%s\");%n", record.get(Field.Subtag).toLowerCase(Locale.ROOT),
                    record.get(Field.PreferredValue));
        }
        System.out.println();

        /* Generate LanguageSubtagRegistryData#languageData entries */
        System.out.println("--- [LanguageSubtagRegistryData#languageData] ---");
        for (Record record : language) {
            assert !record.has(Field.Prefix);
            System.out.printf("map.put(\"%s\", \"%s\");%n", record.get(Field.Subtag), record.get(Field.PreferredValue));
        }
        System.out.println();

        /* Generate LanguageSubtagRegistryData#grandfatheredData entries */
        System.out.println("--- [LanguageSubtagRegistryData#grandfatheredData] ---");
        for (Record record : grandfathered) {
            assert !record.has(Field.Prefix);
            if (record.has(Field.PreferredValue)) {
                System.out.printf("map.put(\"%s\", \"%s\");%n", record.get(Field.Tag).toLowerCase(Locale.ROOT),
                        record.get(Field.PreferredValue));
            } else {
                System.out.printf("map.put(\"%s\", \"%s\");%n", record.get(Field.Tag).toLowerCase(Locale.ROOT),
                        record.get(Field.Tag));
            }
        }
        System.out.println();

        /* Generate LanguageSubtagRegistryData#redundantData entries */
        System.out.println("--- [LanguageSubtagRegistryData#redundantData] ---");
        for (Record record : redundant) {
            assert !record.has(Field.Prefix);
            System.out.printf("map.put(\"%s\", \"%s\");%n", record.get(Field.Tag).toLowerCase(Locale.ROOT),
                    record.get(Field.PreferredValue));
        }
        System.out.println();
    }

    private enum Field {
        Type("Type"), Tag("Tag"), Subtag("Subtag"), Description("Description"), Added("Added"),
        Deprecated("Deprecated"), PreferredValue("Preferred-Value"), Prefix("Prefix"), SupressScript("Suppress-Script"),
        Macrolanguage("Macrolanguage"), Scope("Scope"), Comments("Comments");

        private final String name;

        private Field(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        static final HashMap<String, Field> byName;

        static {
            HashMap<String, Field> map = new HashMap<>();
            for (Field field : Field.values()) {
                map.put(field.getName(), field);
            }
            byName = map;
        }

        public static Field forName(String name) {
            return byName.get(name);
        }
    }

    private static final class Record {
        EnumMap<Field, String> entries = new EnumMap<>(Field.class);

        boolean has(Field field) {
            return entries.containsKey(field);
        }

        String get(Field field) {
            return entries.get(field);
        }
    }

    private static Record readRecord(ArrayDeque<String> stack) {
        Record rec = new Record();
        for (;;) {
            if (stack.isEmpty()) {
                return rec;
            }
            String line = stack.pop();
            assert !line.isEmpty();
            if ("%%".equals(line)) {
                return rec;
            }
            if (line.charAt(0) == ' ') {
                // continuation
                continue;
            }
            int sep = line.indexOf(':');
            String name = line.substring(0, sep).trim();
            String value = line.substring(sep + 1).trim();
            Field field = Field.forName(name);
            assert field != null;
            switch (field) {
            case Deprecated:
            case PreferredValue:
            case Prefix:
            case Subtag:
            case Tag:
            case Type:
                rec.entries.put(field, value);
                break;
            case Added:
            case Comments:
            case Description:
            case Macrolanguage:
            case Scope:
            case SupressScript:
            default:
                // ignore these
                break;
            }
        }
    }

    /**
     * {@link IntlAbstractOperations#JDK_TIMEZONE_NAMES}
     * 
     * @param tzdataDir
     *            the tzdata directory
     * @throws IOException
     *             if an I/O error occurs
     */
    static void jdkTimezoneNames(Path tzdataDir) throws IOException {
        Pattern pZone = Pattern.compile("Zone\\s+([a-zA-Z0-9_+\\-/]+)\\s+.*");
        Pattern pLink = Pattern.compile("Link\\s+([a-zA-Z0-9_+\\-/]+)\\s+([a-zA-Z0-9_+\\-/]+)(?:\\s+#.*)?");
        Pattern pFileName = Pattern.compile("[a-z0-9]+");

        HashSet<String> ignoreFiles = new HashSet<>(Arrays.asList("backzone"));
        TreeSet<String> names = new TreeSet<>();
        TreeMap<String, String> links = new TreeMap<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tzdataDir)) {
            for (Path path : stream) {
                String filename = Objects.requireNonNull(path.getFileName()).toString();
                if (pFileName.matcher(filename).matches() && !ignoreFiles.contains(filename)) {
                    try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                        for (String line; (line = reader.readLine()) != null;) {
                            if (line.startsWith("Zone")) {
                                Matcher m = pZone.matcher(line);
                                if (!m.matches()) {
                                    System.out.println(line);
                                }
                                String name = m.group(1);
                                boolean changed = names.add(name);
                                assert changed : line;
                            } else if (line.startsWith("Link")) {
                                Matcher m = pLink.matcher(line);
                                if (!m.matches()) {
                                    System.out.println(line);
                                }
                                String target = m.group(1);
                                String source = m.group(2);
                                boolean changed = links.put(source, target) == null;
                                assert changed : String.format("%s: %s", filename, line);
                            }
                        }
                    }
                }
            }
        }

        TreeSet<String> allnames = new TreeSet<>();
        allnames.addAll(names);
        for (Map.Entry<String, String> link : links.entrySet()) {
            assert allnames.contains(link.getValue());
            boolean changed = allnames.add(link.getKey());
            assert changed : link;
        }

        TreeSet<String> ids = new TreeSet<>(TimeZone.getAvailableIDs(SystemTimeZoneType.ANY, null, null));
        for (String id : new HashSet<>(ids)) {
            if (id.startsWith("SystemV/")) {
                ids.remove(id);
            }
        }

        System.out.println(allnames);
        System.out.println(ids.size());
        System.out.println(allnames.size());

        TreeSet<String> jdkTimeZones = new TreeSet<>(ids);
        jdkTimeZones.removeAll(allnames);
        for (String name : jdkTimeZones) {
            System.out.printf("\"%s\",", name);
        }
    }

    /**
     * {@link NumberFormatConstructor#CurrencyDigits(String)}
     * 
     * @param currencyFile
     *            the currency xml-file
     * @throws IOException
     *             if an I/O error occurs
     */
    static void currencyDigits(Path currencyFile) throws IOException {
        try (Reader reader = Files.newBufferedReader(currencyFile, StandardCharsets.UTF_8)) {
            LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
            Document xml = xml(reader);
            NodeList list = xml.getDocumentElement().getElementsByTagName("CcyNtry");
            for (int i = 0, len = list.getLength(); i < len; ++i) {
                Element item = (Element) list.item(i);
                Element code = getElementByTagName(item, "Ccy");
                Element minor = getElementByTagName(item, "CcyMnrUnts");
                if (code == null) {
                    continue;
                }
                String scode = code.getTextContent();
                int iminor = 2;
                try {
                    iminor = Integer.parseInt(minor.getTextContent());
                } catch (NumberFormatException e) {
                }
                if (map.containsKey(scode) && map.get(scode) != iminor) {
                    System.err.println(scode);
                }
                if (iminor != 2 && !map.containsKey(scode)) {
                    map.put(scode, iminor);
                }
            }
            TreeMap<Integer, List<String>> sorted = new TreeMap<>();
            for (Map.Entry<String, Integer> entry : map.entrySet()) {
                List<String> currencies = sorted.get(entry.getValue());
                if (currencies == null) {
                    currencies = new ArrayList<>();
                }
                currencies.add(entry.getKey());
                sorted.put(entry.getValue(), currencies);
            }
            for (Map.Entry<Integer, List<String>> entry : sorted.entrySet()) {
                Collections.sort(entry.getValue());
                for (String c : entry.getValue()) {
                    System.out.printf("case \"%s\":%n", c);
                }
                System.out.printf("    return %d;%n", entry.getKey());
            }
            System.out.println("default:\n    return 2;");
        }
    }

    /**
     * {@link IntlAbstractOperations#oldStyleLanguageTags}
     * 
     * @param cldrMainDir
     *            the CLDR main directory
     * @throws IOException
     *             if an I/O error occurs
     */
    static void oldStyleLanguageTags(Path cldrMainDir) throws IOException {
        try (DirectoryStream<Path> newDirectoryStream = Files.newDirectoryStream(cldrMainDir)) {
            Map<String, String> names = new LinkedHashMap<>();
            Map<String, String> aliased = new LinkedHashMap<>();
            for (Path path : newDirectoryStream) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    Document xml = xml(reader);
                    Element identity = getElementByTagName(xml.getDocumentElement(), "identity");
                    assert identity != null;
                    Element language = getElementByTagName(xml.getDocumentElement(), "language");
                    Element script = getElementByTagName(xml.getDocumentElement(), "script");
                    Element territory = getElementByTagName(xml.getDocumentElement(), "territory");

                    String tag = language.getAttribute("type");
                    if (script != null) {
                        tag += "-" + script.getAttribute("type");
                    }
                    if (territory != null) {
                        tag += "-" + territory.getAttribute("type");
                    }

                    String filename = Objects.requireNonNull(path.getFileName()).toString();
                    filename = filename.substring(0, filename.lastIndexOf('.'));
                    names.put(filename, tag);

                    Element alias = getElementByTagName(xml.getDocumentElement(), "alias");
                    if (alias != null && script == null && territory != null) {
                        aliased.put(tag, alias.getAttribute("source"));
                    }
                }
            }
            Map<String, String> result = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : aliased.entrySet()) {
                String from = entry.getKey();
                String to = names.get(entry.getValue());

                String value = result.get(to);
                if (value == null) {
                    value = "";
                } else {
                    value += ", ";
                }
                value += "\"" + from + "\"";
                result.put(to, value);
            }

            for (Map.Entry<String, String> entry : result.entrySet()) {
                System.out.printf("map.put(\"%s\", new String[]{%s});%n", entry.getKey(), entry.getValue());
            }
        }
    }

    private static Element getElementByTagName(Element element, String tagName) {
        return (Element) element.getElementsByTagName(tagName).item(0);
    }

    private static Document xml(Reader xml) throws IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        // turn off any validation or namespace features
        factory.setNamespaceAware(false);
        factory.setValidating(false);
        List<String> features = Arrays.asList("http://xml.org/sax/features/namespaces",
                "http://xml.org/sax/features/validation",
                "http://apache.org/xml/features/nonvalidating/load-dtd-grammar",
                "http://apache.org/xml/features/nonvalidating/load-external-dtd");
        for (String feature : features) {
            try {
                factory.setFeature(feature, false);
            } catch (ParserConfigurationException e) {
                // ignore invalid feature names
            }
        }

        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            InputSource source = new InputSource(xml);
            return builder.parse(source);
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException(e);
        }
    }
}
