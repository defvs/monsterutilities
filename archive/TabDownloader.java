package xerus.monstercat.tabs;

import xerus.monstercat.downloader.DownloaderSwing;
import xerus.monstercat.downloader.ReleaseFile;
import xerus.util.swing.HintTextField;
import xerus.util.swing.SwingTools;
import xerus.util.swing.bases.BasePanel;
import xerus.util.tools.Tools.MultiConsumer;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;

import static xerus.monstercat.MonsterUtilitiesKt.monsterUtilities;
import static xerus.monstercat.downloader.DownloaderSettingsSwing.*;

public class TabDownloader extends JPanel implements BaseTab {
	
	private static final String[] qualities = new String[]{"MP3 V0", "MP3 V2", "MP3 128", "MP3 320", "FLAC", "WAV"};
	public static final String[] patterns = {"{artistsTitle} - {album} - {track} {title}", "{artistsTitle} - {title}", "{artists|natural} - {title} - {album}", "{artists|, } - {title}"};
	
	public TabDownloader() {
		setPreferredSize(new Dimension(600, 400));
		setLayout(new GridLayout(1, 1));
		add(new Content());
	}
	
	@Override
	public void init() {
		if (LASTDOWNLOADED.get().isEmpty())
			return;
		int continueDownload = JOptionPane.showConfirmDialog(this, "We have noticed that your last download was not finished. Want to pick up where you left off last time?",
				"Downloader", JOptionPane.YES_NO_CANCEL_OPTION);
		switch (continueDownload) {
			case 0:
				monsterUtilities.getTabPane().getSelectionModel().select(monsterUtilities.getTabs().indexOf(this));
				Content.content.buttonCall(-1);
				break;
			case 1:
				LASTDOWNLOADED.put("");
				break;
		}
	}
	
	@Override
	public void remove(Component comp) {
		super.remove(comp);
		if (comp.getClass() == Content.class)
			add(new DownloaderSwing());
		if (comp.getClass() == DownloaderSwing.class)
			add(new Content());
	}
	
	public static class Content extends BasePanel {
		
		static Content content;
		
		@Override
		protected void registerComponents() {
			content = this;
			regFileChooser("Download directory").setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			DONWLOADDIR.addField(fileChooserField);
			
			regLabel("Types", 0);
			JPanel typePanel = new JPanel();
			typePanel.setLayout(new BoxLayout(typePanel, BoxLayout.X_AXIS));
			reg(typePanel, 1);
			MultiConsumer<String> addType = (name) -> {
				String key = name.length > 2 ? name[2] : name[0];
				JCheckBox jcb = TYPES.addCheckbox(name[0], key);
				if (name.length > 1)
					jcb.setToolTipText(name[1]);
				typePanel.add(jcb);
			};
			addType.accept("Single");
			addType.accept("EP/Album", "All EPs and Albums except the Monstercat Collections");
			addType.accept("Monstercat Collection", "Monstercat 0xx / Uncaged", "Monstercat Collection");
			addType.accept("Monstercat Best of", "Monstercat - Best of / Monstercat Anniversaries", "Best of");
			addType.accept("Podcast", "Podcasts (Music only if possible)");
			
			regLabel("Limit amount", 0);
			JSpinner limitSpinner = new JSpinner(new SpinnerNumberModel(LIMIT.asInt(), 0, 5000, 1));
			limitSpinner.setToolTipText("Amount of Releases to download - 0 means everything");
			reg(LIMIT.addField(limitSpinner), 1);
			
			regLabel("Album Mixes", 0);
			reg(ALBUMMIXES.addField(new JComboBox<>(new String[]{"Include", "Exclude", "Separate"})), 1);
			
			String[][] names = {{"Fast Skip", "fastskip", "Assumes that everything present in the directory was correctly downloaded"}, {"New Releases first", "newestfirst"}};
			JCheckBox[] checkboxes = new JCheckBox[names.length];
			for (int i = 0; i < names.length; i++) {
				String[] cur = names[i];
				checkboxes[i] = MISC.addCheckbox(cur[0], cur[1]);
				if (cur.length > 2)
					checkboxes[i].setToolTipText(cur[2]);
			}
			
			JPanel miscPanel = new JPanel();
			miscPanel.setLayout(new BoxLayout(miscPanel, BoxLayout.X_AXIS));
			for (JCheckBox checkbox : checkboxes)
				miscPanel.add(checkbox);
			reg(miscPanel);
			
			add(new JLabel("File naming pattern"), constraints(0).setGridHeight(2));
			JComboBox<String> pattern = new JComboBox<>(patterns);
			pattern.setEditable(true);
			reg(FILENAMEPATTERN.addField(pattern), 1);
			JTextComponent patternTextfield = (JTextComponent) pattern.getEditor().getEditorComponent();
			SwingTools.addChangeListener(patternTextfield, c -> checkPattern(patternTextfield.getText()));
			patternLabel = regLabel(null, 1);
			checkPattern();
			
			reg(QUALITY.addField(new JComboBox<>(qualities)));
			JTextField connectsid = new HintTextField("connect.sid");
			connectsid.setToolTipText(
					"Log into monstercat.com from your browser, find the cookie \"connect.sid\" from \"api.monstercat.com\" and copy the long character sequence (usually starting with \"s%3A\") into here");
			reg(CONNECTSID.addField(connectsid));
			
			regButton("Start Download");
		}
		
		@Override
		public void buttonCall(int buttonid) {
			if(CONNECTSID.get().isEmpty())
				showMessage("Please enter a connect.sid", "No api.sid", "Warn");
			else if (!checkPattern())
				showMessage("Your file naming pattern is not valid", "Invalid Pattern", "Warn");
			else {
				if (buttonid != -1)
					LASTDOWNLOADED.put("");
				getParent().remove(this);
			}
		}
		
		private JLabel patternLabel;
		private static final ReleaseFile testRelease = new ReleaseFile("Rogue, Stonebank & Slips & Slurs - Monstercat Uncaged Vol. 1 - 1 Unity");
		
		boolean checkPattern(String... pattern) {
			String p;
			if (pattern.length > 0)
				p = pattern[0];
			else
				p = FILENAMEPATTERN.get();
			try {
				patternLabel.setText(testRelease.toString(p));
				return true;
			} catch(NoSuchFieldException e) {
				patternLabel.setText("No such field: " + e.getMessage());
			} catch(Exception e) {
				showError(e, "Error parsing pattern");
			}
			return false;
		}
		
	}
	
	/* Signing in within the application did not return cookies, so I'll have to use the connect.sid for now
	 *
	 * private JTextField email; private JTextField password;
	 *
	 * email = new HintTextField("Email address"); password = //new
	 * HintTextField("Password"); new JPasswordField(); reg(email); reg(password); regButton("Submit", 0, 2);
	 *
	 * CookieManager cookieJar = new CookieManager(null, CookiePolicy.ACCEPT_ALL); CookieHandler.setDefault(cookieJar);
	 * HttpURLConnection connection = ConnectionTools.createConnection(MonstercatConnect.concat("signin"));
	 * connection.setRequestProperty("User-Agent",
	 * "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:55.0) Gecko/20100101 Firefox/55.0" );
	 * ConnectionTools.Post(connection, "email="+email.getText(), "password="+password.getText());
	 * ConnectionTools.dumpResponse(connection); //cookieJar.put(new URI(connect.concat("signin")),
	 * connection.getHeaderFields()); for (String cookie : cookies) { connection.addRequestProperty("Cookie",
	 * cookie.split(";", 2)[0]); } */
	
}
