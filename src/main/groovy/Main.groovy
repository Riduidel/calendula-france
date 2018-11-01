@Grapes([
    @Grab('org.ccil.cowan.tagsoup:tagsoup:1.2.1'),
    @Grab(group='org.codehaus.gpars', module='gpars', version='1.2.1')
])
import groovy.cli.commons.*
import groovy.transform.*
import groovy.util.logging.*
import org.ccil.cowan.tagsoup.Parser
import groovyx.gpars.actor.*

@Log 
public class Main {
    public static final String PUBLIC_MEDICINE_DATABASE = "http://base-donnees-publique.medicaments.gouv.fr/telechargement.php"
    public static final String TEMPORARY_FOLDER = "target/files"
    public static final String DESTINATION_FILE = "target/HAS.sql"

    public static void main(String[] args) {
        def cli = new CliBuilder(usage: 'Main.groovy ...')
        // Create the list of options.
        cli.h(longOpt: 'help', 'Show usage information')
        cli.o(longOpt: 'override', 'Override downloaded files')
        cli.s(longOpt: 'source', args:1, defaultValue: PUBLIC_MEDICINE_DATABASE, 
                "URL of website where to download files from. Defaults to ${PUBLIC_MEDICINE_DATABASE}")
        cli.t(longOpt: 'temporaryFolder', args:1, defaultValue: TEMPORARY_FOLDER, 
                "Temporary folder for downloaded files. Defaults to ${TEMPORARY_FOLDER}")
        cli.d(longOpt: 'destination', args:1, defaultValue: DESTINATION_FILE, 
                "Destination file storing all SQL statements. Defaults to ${DESTINATION_FILE}")
        
        def options = cli.parse(args)
        if (!options) {
            return
        }
        // Show usage text when -h or --help option is used.
        if (options.h) {
            cli.usage()
            return
        }
        new Main().transform(options)
    }

    public void transform(OptionAccessor options) {
        Actor openPage = new OpenPageActor(options)
        openPage.start()
        openPage.send options.s
        openPage.join()
    }

}
@Log 
class SQLWriter extends DefaultActor  {
    private OptionAccessor options
    private File destination
    private Set<String> processors = new HashSet()
    private Set<String> lines = new TreeSet()
    public SQLWriter(OptionAccessor options) {
        super()
        this.options = options
        this.destination = new File(options.d)
        destination.getParentFile().mkdirs()
        destination.delete()
    }
    public void afterStop(List undeliveredMessages) {
        destination.append lines.join(), "UTF-8"
        log.info "All lines have been written to ${destination.absolutePath}"
        groovyx.gpars.GParsConfig.shutdown()
    }
    @Override protected void act() {
        loop {
            react { sqlLine ->
                if (sqlLine.startsWith(FileProcessor.START)) {
                    processors.add(sqlLine.substring(FileProcessor.START.size()))
                    log.info "Added one processor. Processors are ${processors}"
                } else if (sqlLine.startsWith(FileProcessor.STOP)) {
                    processors.remove(sqlLine.substring(FileProcessor.STOP.size()))
                    log.info "Removed one processor. Processors are ${processors}"
                    if (processors.size()<=0) {
                        log.info "All processors should have terminated now. Stopping agent."
                        stop()
                    }
                } else {
                    lines << sqlLine
                }
            }
        }
    }
}
@Log 
class OpenPageActor extends DefaultActor  {
    private OptionAccessor options
    private boolean override
    public OpenPageActor(OptionAccessor options) {
        super()
        this.options = options
        this.override = options.o
    }
    @Override protected void act() {
        loop {
            react { url ->
                log.info "Reading file list from ${url}"
                def tagsoupParser = new org.ccil.cowan.tagsoup.Parser()
                def slurper = new XmlParser(tagsoupParser)
                def htmlPage = slurper.parse(url)
                log.fine "Read ${url}"
                def content = htmlPage.body[0]
                    .find { it["@id"]=='container'}
                    .div[0]
                def lists = content.ul
                def lastList = lists[lists.size()-1]
                SQLWriter  writer = new SQLWriter(options).start()
                // This last list contains all the files we have to download
                lastList.children().each {
                    def text = it.a.text()
                    def address = it.a["@href"][0]
                   log.info "We need to download \"${text}\""
                    def downloader = new DownloadFileActor(options, writer).start()
                    downloader.send address
                }
                writer.join()
                stop()
            }
        }
    }
}


@Log 
class DownloadFileActor extends DefaultActor  {
    private OptionAccessor options
    private File folder
    private String baseUrl
    private boolean override
    private SQLWriter writer
    public DownloadFileActor(OptionAccessor options, SQLWriter writer) {
        super()
        this.options = options
        this.baseUrl = options.s
        this.folder = new File(options.t)
        this.folder.mkdirs()
        this.override = options.o
        this.writer = writer
    }

    @Override protected void act() {
        loop {
            react { url ->
                def fileName = url.substring(url.indexOf('=')+1)
                File destination = new File(this.folder, fileName)
                if (override || !destination.exists()) {
                    def realUrl = this.baseUrl+url
                    log.info "Downloading file from ${realUrl} to ${destination}"
                    def outputStream = destination.newOutputStream()  
                    outputStream << new URL(realUrl).openStream()  
                    outputStream.close()  
                    log.info "Downloaded file from ${realUrl} to ${destination}"
                }
                // Now open file, read each line, and send it to the right actor
                def reader = createReader(fileName)
                log.info "Reading lines of ${fileName}"
                destination.eachLine('ISO-8859-1') {
                    reader.send it
                }
                reader.send FileProcessor.STOP
                stop()
            }
        }
    }

    private Actor createReader(String fileName) {
        switch(fileName) {
            case "CIS_bdpm.txt": 
                return new CIS_bdpm(options, writer).start()
            case "CIS_CIP_bdpm.txt": 
                return new CIS_CIP_bdpm(options, writer).start()
            case "CIS_COMPO_bdpm.txt": 
                return new CIS_COMPO_bdpm(options, writer).start()
            case "CIS_CPD_bdpm.txt": 
                return new CIS_CPD_bdpm(options, writer).start()
            case "CIS_GENER_bdpm.txt": 
                return new CIS_GENER_bdpm(options, writer).start()
            case "CIS_HAS_ASMR_bdpm.txt": 
                return new CIS_HAS_ASMR_bdpm(options, writer).start()
            case "CIS_HAS_SMR_bdpm.txt": 
                return new CIS_HAS_SMR_bdpm(options, writer).start()
            case "CIS_InfoImportantes.txt": 
                return new CIS_InfoImportantes(options, writer).start()
            case "HAS_LiensPageCT_bdpm.txt": 
                return new HAS_LiensPageCT_bdpm(options, writer).start()
        }
    }
}

@Log class FileProcessor extends DefaultActor  {
    public static final String STOP = "stop:"
    public static final String START = "start:"
    private OptionAccessor options
    SQLWriter writer
    public FileProcessor(OptionAccessor options, SQLWriter writer) {
        super()
        this.options = options
        this.writer = writer
        writer.send START+this.class.name
    }
    @Override protected void act() {
        loop {
            react { line ->
                if (STOP==line) {
                    writer.send STOP+this.class.name
                    stop()
                } else {
                    processLine line
                }
            }
        }
    }

    protected void processLine(String line) {
        processLineFragments(line.split('\t'))
    }

    protected void processLineFragments(String[] lineFragments) {}

    public void afterStop(List undeliveredMessages) {
        log.info "All lines of ${this.class.name} should have been processed."
    }

    public String safe(String value) {
        return value.replace("\'", "\'\'")
    }
}

@Log class CIS_bdpm extends FileProcessor {
    public CIS_bdpm(OptionAccessor options, SQLWriter writer) {
        super(options, writer)
    }

    protected void processLineFragments(String[] lineFragments) {
        // TODO restore presentation here
        writer << """
INSERT INTO -- 01 - prescription - ${lineFragments[1]}
    Prescription ("Code","Name", "PresentationForm") VALUES 
    ('${safe(lineFragments[0])}', '${safe(lineFragments[1])}', '${safe(lineFragments[2])}'' );"""
    }
}
@Log class CIS_CIP_bdpm extends FileProcessor {
    public CIS_CIP_bdpm(OptionAccessor options, SQLWriter writer) {
        super(options, writer)
    }

    protected void processLineFragments(String[] lineFragments) {
        writer << """
INSERT INTO -- 02- presentation form - ${lineFragments[1]}
    PresentationForm ("Name", "PresentationFormId") VALUES 
    ('${safe(lineFragments[2])}', '${safe(lineFragments[6])}' );"""
    }
}
@Log class CIS_COMPO_bdpm extends FileProcessor {
    public CIS_COMPO_bdpm(OptionAccessor options, SQLWriter writer) {
        super(options, writer)
    }

    protected void processLineFragments(String[] lineFragments) {
        writer << """
INSERT INTO -- 03- active ingredient - ${lineFragments[2]}
    ActiveIngredient ("ActiveIngredientID", "Name") VALUES 
    ('${safe(lineFragments[2])}', '${safe(lineFragments[4])}' );"""
        writer << """
INSERT INTO -- 13- active ingredient prescription link form - ${lineFragments[4]}
    PrescriptionActiveIngredient ("PrescriptionCode", "ActiveIngredientID") VALUES 
    ('${safe(lineFragments[0])}', '${safe(lineFragments[2])}' );"""
    }
}
@Log class CIS_CPD_bdpm extends FileProcessor {
    public CIS_CPD_bdpm(OptionAccessor options, SQLWriter writer) {
        super(options, writer)
    }
}
@Log class CIS_GENER_bdpm extends FileProcessor {
    public CIS_GENER_bdpm(OptionAccessor options, SQLWriter writer) {
        super(options, writer)
    }
}
@Log class CIS_HAS_ASMR_bdpm extends FileProcessor {
    public CIS_HAS_ASMR_bdpm(OptionAccessor options, SQLWriter writer) {
        super(options, writer)
    }
}
@Log class CIS_HAS_SMR_bdpm extends FileProcessor {
    public CIS_HAS_SMR_bdpm(OptionAccessor options, SQLWriter writer) {
        super(options, writer)
    }
}
@Log class CIS_InfoImportantes extends FileProcessor {
    public CIS_InfoImportantes(OptionAccessor options, SQLWriter writer) {
        super(options, writer)
    }
}
@Log class HAS_LiensPageCT_bdpm extends FileProcessor {
    public HAS_LiensPageCT_bdpm(OptionAccessor options, SQLWriter writer) {
        super(options, writer)
    }
}
