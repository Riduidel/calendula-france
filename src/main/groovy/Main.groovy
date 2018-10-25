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
        cli.d(longOpt: 'tdestination', args:1, defaultValue: DESTINATION_FILE, 
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
    public SQLWriter(OptionAccessor options) {
        super()
        this.options = options
        this.destination = new File(options.d)
        destination.getParentFile().mkdirs()
        destination.delete()
    }
    @Override protected void act() {
        loop {
            react { sqlLine ->
               destination << sqlLine
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
                    downloader.join()
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
                if (override) {
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
                destination.readLines().each {
                    reader.send it
                }
            }
            stop()
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

@Log public class CIS_bdpm extends DefaultActor {
    private SQLWriter writer
    public CIS_bdpm(OptionAccessor options, SQLWriter writer) {
        super()
        this.writer = writer
    }
    @Override protected void act() {
        loop {
            react { line ->
            }
        }
    }
}
@Log public class CIS_CIP_bdpm extends DefaultActor {
    private SQLWriter writer
    public CIS_CIP_bdpm(OptionAccessor options, SQLWriter writer) {
        super()
        this.writer = writer
    }
    @Override protected void act() {
        loop {
            react { line ->
            }
        }
    }
}
@Log public class CIS_COMPO_bdpm extends DefaultActor {
    private SQLWriter writer
    public CIS_COMPO_bdpm(OptionAccessor options, SQLWriter writer) {
        super()
        this.writer = writer
    }
    @Override protected void act() {
        loop {
            react { line ->
            }
        }
    }
}
@Log public class CIS_CPD_bdpm extends DefaultActor {
    private SQLWriter writer
    public CIS_CPD_bdpm(OptionAccessor options, SQLWriter writer) {
        super()
        this.writer = writer
    }
    @Override protected void act() {
        loop {
            react { line ->
            }
        }
    }
}
@Log public class CIS_GENER_bdpm extends DefaultActor {
    private SQLWriter writer
    public CIS_GENER_bdpm(OptionAccessor options, SQLWriter writer) {
        super()
        this.writer = writer
    }
    @Override protected void act() {
        loop {
            react { line ->
            }
        }
    }
}
@Log public class CIS_HAS_ASMR_bdpm extends DefaultActor {
    private SQLWriter writer
    public CIS_HAS_ASMR_bdpm(OptionAccessor options, SQLWriter writer) {
        super()
        this.writer = writer
    }
    @Override protected void act() {
        loop {
            react { line ->
            }
        }
    }
}
@Log public class CIS_HAS_SMR_bdpm extends DefaultActor {
    private SQLWriter writer
    public CIS_HAS_SMR_bdpm(OptionAccessor options, SQLWriter writer) {
        super()
        this.writer = writer
    }
    @Override protected void act() {
        loop {
            react { line ->
            }
        }
    }
}
@Log public class CIS_InfoImportantes extends DefaultActor {
    private SQLWriter writer
    public CIS_InfoImportantes(OptionAccessor options, SQLWriter writer) {
        super()
        this.writer = writer
    }
    @Override protected void act() {
        loop {
            react { line ->
            }
        }
    }
}
@Log public class HAS_LiensPageCT_bdpm extends DefaultActor {
    private SQLWriter writer
    public HAS_LiensPageCT_bdpm(OptionAccessor options, SQLWriter writer) {
        super()
        this.writer = writer
    }
    @Override protected void act() {
        loop {
            react { line ->
            }
        }
    }
}