package io.github.brickwall2900.processing.demo.chat;

import io.github.brickwall2900.processing.ProcessManager;
import io.github.brickwall2900.processing.ProcessManagerMaster;
import io.github.brickwall2900.processing.info.ChildProcessInfo;
import io.github.brickwall2900.processing.messaging.Messenger;
import io.github.brickwall2900.processing.messaging.acceptors.MessageAcceptor;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.*;
import java.util.List;

/// ChatMainApp is a very rough demo showing the capabilities of Messenger and its basic functionality.
///
/// key points in this class:
/// @see ChatMainApp#ChatMainApp()
/// @see ChatMainApp#accept(UUID, UUID, String, MessageType, String, UUID)
/// @see ChatMainApp#onSendButtonPressed(ActionEvent)
/// @see ChatMainApp#onChildProcessButtonPressed(ActionEvent)
/// @see ChatMainApp#onAddChannelButtonPressed(ActionEvent)
/// @see ChatMainApp#onAddDMButtonPressed(ActionEvent)
public class ChatMainApp extends JFrame implements MessageAcceptor {
    public static final AudioFormat SOUND_FORMAT = new AudioFormat(22050, 16, 1, true, false);
    private static final int MAX_MESSAGES = 5000;

    public static void main() {
        SwingUtilities.invokeLater(ChatMainApp::swingMain);
    }

    private static void swingMain() {
        ChatMainApp chatMainApp = new ChatMainApp();
        chatMainApp.setVisible(true);
    }

    public static final String BROADCAST_TAB_NAME = "Global";
    public static final String HEADER_FORMAT = "Chat: logged is as %s";
    public static final String MESSAGE_FORMAT = "---%s---%n[%s] %s%n%n";
    public static final String REPLIED_MESSAGE_FORMAT = "Replying to \"%s\":%n---%s---%n[%s] %s%n%n";
    public static final String TITLE = "ChatDemoApp: %s";

    private final List<MessageInfo> messages = new ArrayList<>();
    private final SwingUserdata userdata;
    private final ProcessManager processManager;
    private final Messenger messenger;

    // a list of all scroll panes in the tabbed pane
    private final List<JScrollPane> scrollPaneList;

    private byte[] receiveSound, sendSound;

    private JLabel headerLabel;
    private JTabbedPane tabbedPane;

    private JCheckBox enableSoundsCheckbox;
    private JTextField messageField;
    private JButton sendButton;

    public ChatMainApp() {
        // here we get the process manager as usual
        // we are literally combining two codebases as one ;-;
        if (ProcessManager.getInstance().isChild()) {
            processManager = ProcessManager.getInstance().asChild();
        } else {
            processManager = ProcessManager.getInstance().asMaster();
        }
        messenger = processManager.getMessenger();

        if (!processManager.isChild()) {
            // if we are the master, then
            // set password provider and
            // start the master server
            processManager.setPasswordProvider(new ChatPasswordProvider());
            try {
                processManager.asMaster().startMaster(3456);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        // subscribe to messages right now
        messenger.on(this);

        // see SwingUserdata for more info
        userdata = new SwingUserdata();
        scrollPaneList = new ArrayList<>();

        // other crap im doing is just UI headache
        // UX is just at the negatives atp btw
        createEverythingElse();

        // then load sounds
        loadSounds();
    }

    private void loadSounds() {
        try (InputStream receiveSoundStream =
                     new BufferedInputStream(Objects.requireNonNull(getClass().getResourceAsStream("receive.wav")));
             InputStream sendSoundStream =
                     new BufferedInputStream(Objects.requireNonNull(getClass().getResourceAsStream("send.wav")));
             AudioInputStream receiveAudioStream =
                     AudioSystem.getAudioInputStream(receiveSoundStream);
             AudioInputStream sendAudioStream =
                     AudioSystem.getAudioInputStream(sendSoundStream)) {
            receiveSound = receiveAudioStream.readAllBytes();
            sendSound = sendAudioStream.readAllBytes();
        } catch (IOException | UnsupportedAudioFileException e) {
            System.err.println("Error loading sounds ;(");
            e.printStackTrace();
        }
    }

    private void createEverythingElse() {
        tabbedPane = new JTabbedPane();
        createTab(BROADCAST_TAB_NAME, MessageType.BROADCAST, null, null);

        JPanel contentPane = new JPanel(new BorderLayout(4, 4));
        JPanel headerPane = createHeaderPane();
        JPanel footerPane = createFooterPane();

        contentPane.add(headerPane, BorderLayout.NORTH);
        contentPane.add(tabbedPane, BorderLayout.CENTER);
        contentPane.add(footerPane, BorderLayout.SOUTH);
        contentPane.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                onWindowClosed();
            }
        });

        setTitle(TITLE.formatted(processManager.getMyId().toString()));
        setContentPane(contentPane);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE); // we handle closing ourselves
        pack();
        setLocationByPlatform(true);
    }

    private JPanel createFooterPane() {
        JPanel footerPane = new JPanel(new BorderLayout(4, 4));

        enableSoundsCheckbox = new JCheckBox("Sounds");
        messageField = new JTextField();
        sendButton = new JButton("Send!");

        messageField.setFont(Font.decode(Font.MONOSPACED));

        messageField.addActionListener(this::onEnterTextboxPressed);
        sendButton.addActionListener(this::onSendButtonPressed);

        footerPane.add(enableSoundsCheckbox, BorderLayout.WEST);
        footerPane.add(messageField, BorderLayout.CENTER);
        footerPane.add(sendButton, BorderLayout.EAST);

        return footerPane;
    }

    private JPanel createHeaderPane() {
        JPanel headerPane = new JPanel();

        GroupLayout layout = new GroupLayout(headerPane);
        layout.setAutoCreateGaps(true);
        headerPane.setLayout(layout);

        JButton addDirectMessageButton = new JButton("Add DM to Client");
        JButton addChannelButton = new JButton("Add to Channel");
        headerLabel = new JLabel(HEADER_FORMAT.formatted(processManager.getMyId()));

        boolean isMaster = !processManager.isChild();
        JButton addClientButton = null;
        if (isMaster) {
            addClientButton = new JButton("Spawn Child Process");
        }

        addChannelButton.addActionListener(this::onAddChannelButtonPressed);
        addDirectMessageButton.addActionListener(this::onAddDMButtonPressed);
        if (isMaster) {
            addClientButton.addActionListener(this::onChildProcessButtonPressed);
        }

        GroupLayout.Group horizontalGroup = layout.createSequentialGroup()
                .addComponent(headerLabel, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(addDirectMessageButton)
                .addComponent(addChannelButton);

        GroupLayout.Group verticalGroup = layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(headerLabel)
                .addComponent(addDirectMessageButton)
                .addComponent(addChannelButton);

        if (isMaster) {
            horizontalGroup.addComponent(addClientButton);
            verticalGroup.addComponent(addClientButton);
        }

        layout.setHorizontalGroup(horizontalGroup);
        layout.setVerticalGroup(verticalGroup);

        headerPane.add(headerLabel);
        headerPane.add(addDirectMessageButton);
        headerPane.add(addChannelButton);

        if (isMaster) {
            headerPane.add(addClientButton);
        }

        headerPane.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        return headerPane;
    }

    private TabInfo createTab(String tabName, MessageType type, String channel, UUID target) {
        JTextArea chatbox = new JTextArea();
        JScrollPane chatboxScrollPane = new JScrollPane(chatbox);

        chatbox.setFont(Font.decode(Font.MONOSPACED));

        chatbox.setEditable(false);
        chatboxScrollPane.setPreferredSize(new Dimension(800, 600));

        // print first time message
        if (tabbedPane.getTabCount() <= 0) {
            printFirstTimeMessage(chatbox);
        }

        tabbedPane.addTab(tabName, chatboxScrollPane);

        int index = tabbedPane.getTabCount() - 1;
        tabbedPane.setSelectedIndex(index);

        this.scrollPaneList.add(chatboxScrollPane);

        TabInfo info = new TabInfo(type, index, channel, target);
        this.userdata.putUserdata(chatboxScrollPane, info);
        return info;
    }

    private void printFirstTimeMessage(JTextArea chatbox) {
        chatbox.append("""
                Welcome to this demo! This shows the capabilities of the Messenger class in ProcessManager.
                Messages are formatted like this:
                
                --- <message-id> ---
                [<process-id>] <message>
                
                Try spawning new processes and make a broadcast message.
                
                (hint: use "replyto:<message-id>" in the message field to reply to a message)
                
                """);
    }

    private void onWindowClosed() {
        if (!processManager.isChild()) {
            // as the master, when the window is closed
            // we shut down the master server
            // that subsequently shuts down all child processes too so it'll be okay...
            try {
                processManager.asMaster().stopMaster();
            } catch (InterruptedException _) {
            }
        }
        System.exit(0);
        // as the child, we just exit through System.exit and run the shutdown hook provided by ProcessManager for us
    }

    private void onSendButtonPressed(ActionEvent e) {
        // if the send button is pressed
        // then depending on where we are,
        // broadcast it or send it in a channel.

        // uh oh! dunno if we're doing the right thing here
        // but this is just a quick demo i promise i am not putting that on production code

        // cast this to a scroll pane? what if it's not???
        JScrollPane scrollPane = (JScrollPane) tabbedPane.getSelectedComponent();
        TabInfo info = (TabInfo) userdata.getUserdata(scrollPane);

        String text = consumeMessageField();
        String rawtext;
        UUID replyTo = null;
        if (text.startsWith("replyto:")) {
            String first = null;
            try {
                String[] split = text.split("\\s+");
                first = split[0];
                replyTo = UUID.fromString(first.replace("replyto:", ""));
                rawtext = Arrays.stream(split).skip(1).reduce("", (x, y) -> x + ' ' + y).substring(1);
            } catch (IllegalArgumentException _) {
                JOptionPane.showMessageDialog(this,
                        "Badly formatted UUID: " + first,
                        getTitle(),
                        JOptionPane.ERROR_MESSAGE);
                messageField.setText(text); // put the damn text back
                return;
            }
        } else {
            rawtext = text;
        }

        // check for info type, direct replies are only supported
        if (replyTo != null && (info.type() == MessageType.BROADCAST || info.type() == MessageType.CHANNEL)) {
            JOptionPane.showMessageDialog(this,
                    "Message replies are only supported in direct messages.");
            messageField.setText(text); // put the damn text back here too
            return;
        }

        UUID messageId = null;
        switch (info.type()) {
            case BROADCAST -> {
                messageId = messenger.messageBroadcast(rawtext);
                chatLog(info.type(), info.channel(), null, processManager.getMyId(),
                        messageId != null ? messageId.toString() : "?", rawtext, null);
            }
            case CHANNEL -> {
                // okay so we have to get the channel
                String channelName = info.channel();
                messageId = messenger.messagePublish(channelName, rawtext);
                chatLog(info.type(), info.channel(), null, processManager.getMyId(),
                        messageId != null ? messageId.toString() : "?", rawtext, null);
            }
            case DIRECT -> {
                // same thing here
                UUID directUUID = info.target();
                if (replyTo != null) {
                    messageId = messenger.messageDirectReply(directUUID, rawtext, replyTo);
                } else {
                    messageId = messenger.messageDirect(directUUID, rawtext);
                }
                chatLog(info.type(), info.channel(), directUUID, processManager.getMyId(),
                        messageId != null ? messageId.toString() : "?", rawtext, replyTo);
            }
        }

        if (messageId != null) {
            messages.add(new MessageInfo(
                    messageId,
                    processManager.getMyId(),
                    info.channel(),
                    info.type(),
                    rawtext,
                    replyTo));
        }

        playSendSound();
        // okay i realized now that this maybe isn't the best thing to do
        // since what if
        // the receive sound can play on other processes
        // and this overlaps the send sound sent by this process

        // however, it's toggleable...
    }

    private void onChildProcessButtonPressed(ActionEvent e) {
        // okay here's the part where we spawn a child process
        try {
            try (ProcessManagerMaster.ChildProcessResult processResult =
                         processManager.asMaster().spawnChildProcess(new ChildProcessInfo()
                                 // here i'll just use the exact same class
                                 // i don't want to write duplicates
                                 // i'll just put a check if it's a child bruh
                                 .mainClass(ChatMainApp.class)
                                 .earlyBootRunnableClass(ChatEarlyBoot.class))) {
                // block the EDT and wait
                processManager.asMaster().waitForConnection(processResult.childProcessId(), Duration.ofSeconds(10));

                if (!processManager.isConnected(processResult.childProcessId())) {
                    JOptionPane.showMessageDialog(null, "Could not spawn child process! " +
                            "Please see console for details.", getTitle(), JOptionPane.ERROR_MESSAGE);
                    processManager.asMaster().forceTerminate(processResult.childProcessId());
                }

                headerLabel.setText(HEADER_FORMAT.formatted(processManager.getMyId()));
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onAddChannelButtonPressed(ActionEvent e) {
        String channelName = JOptionPane.showInputDialog(this, "Create a new channel name");
        if (channelName == null) {
            return;
        }

        // check if channel tab exists
        // admittedly, aAHDSHDAFAKJSHFKJASDJKLS
        // fuck me ;-;
        // who the FUCK does THIS is their swing UI code ;-;
        for (JScrollPane scrollPane : scrollPaneList) {
            TabInfo info = (TabInfo) userdata.getUserdata(scrollPane);
            if (Objects.equals(info.channel(), channelName)) {
                tabbedPane.setSelectedIndex(info.tabIndex());
                return;
            }
        }

        // add channel tab pane
        createTab("Channel: " + channelName, MessageType.CHANNEL, channelName, null);

        // subscribe to channel
        messenger.subscribe(channelName);

        // okay then we let Messenger handle the message routing for us
        // from what i've written, the master Messenger implementation
        // fires MessageAcceptors if they are actually subscribed to that channel...
    }

    private void onAddDMButtonPressed(ActionEvent e) {
        // we choose the process id to DM
        UUID chosenProcessId = showProcessIdChooserDialog("Who to directly message?");
        if (chosenProcessId == null) {
            return;
        }

        // check if such tab exists
        for (JScrollPane scrollPane : scrollPaneList) {
            TabInfo info = (TabInfo) userdata.getUserdata(scrollPane);
            if (Objects.equals(info.target(), chosenProcessId)) {
                tabbedPane.setSelectedIndex(info.tabIndex());
                return;
            }
        }

        // add teh tab pane
        createTab("Direct: " + chosenProcessId, MessageType.DIRECT, null, chosenProcessId);

        // and we don't need to do anything else
    }

    private void onEnterTextboxPressed(ActionEvent e) {
        sendButton.doClick();
    }

    private UUID showProcessIdChooserDialog(String message) {
        JPanel content = new JPanel(new BorderLayout(4, 4));
        JLabel header = new JLabel(message);
        JComboBox<UUID> comboBox = new JComboBox<>(processManager.getProcesses().toArray(UUID[]::new));

        content.add(header, BorderLayout.NORTH);
        content.add(comboBox, BorderLayout.CENTER);


        int result = JOptionPane.showConfirmDialog(this, content, getTitle(), JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION) {
            return (UUID) comboBox.getSelectedItem();
        } else {
            return null;
        }
    }

    private String consumeMessageField() {
        String text = messageField.getText();
        messageField.setText(null);
        return text;
    }

    private MessageInfo findMessageInfo(UUID messageId) {
        for (MessageInfo info : messages) {
            if (Objects.equals(info.messageId(), messageId)) {
                return info;
            }
        }
        return null;
    }

    private TabInfo getTabInfo(MessageType messageType, String channel, UUID sender) {
        for (JScrollPane scrollPane : scrollPaneList) {
            TabInfo info = (TabInfo) userdata.getUserdata(scrollPane);
            if (info.matches(messageType, channel, sender)) {
                return info;
            }
        }

        return null;
    }

    private void chatLog(MessageType messageType,
                         String channel,
                         UUID target,
                         UUID sender,
                         String messageId,
                         String message,
                         UUID replyTo) {
        // soo...
        // one works
        // other doesn't
        // why not both?

        TabInfo tabInfo = getTabInfo(messageType, channel, target);
        TabInfo remoteTabInfo = getTabInfo(messageType, channel, sender);
        if (remoteTabInfo == null && tabInfo == null) {
            return;
        }

        chatLogToTextarea(tabInfo, sender, messageId, message, replyTo);
        if (tabInfo == null) {
            chatLogToTextarea(remoteTabInfo, sender, messageId, message, replyTo);
        }
    }

    private void chatLogToTextarea(TabInfo tabInfo,
                                   UUID sender,
                                   String messageId,
                                   String message,
                                   UUID replyTo) {
        if (tabInfo == null) {
            return;
        }

        JScrollPane scrollPane = (JScrollPane) tabbedPane.getComponentAt(tabInfo.tabIndex());
        JTextArea textArea = (JTextArea) scrollPane.getViewport().getView();

        if (replyTo != null) {
            MessageInfo info = findMessageInfo(replyTo);
            textArea.append(REPLIED_MESSAGE_FORMAT.formatted(
                    info != null ? info.message() : "< message not found ;( >",
                    messageId,
                    sender,
                    message));
        } else {
            textArea.append(MESSAGE_FORMAT.formatted(messageId, sender, message));
        }

        JViewport viewport = scrollPane.getViewport();
        if (viewport.getViewSize().height > tabbedPane.getHeight()) {
            viewport.setViewPosition(new Point(0, Short.MAX_VALUE));
        }
    }

    private void createTabForDM(UUID processIdSender) {
        // check if such tab exists
        for (JScrollPane scrollPane : scrollPaneList) {
            TabInfo info = (TabInfo) userdata.getUserdata(scrollPane);
            if (Objects.equals(info.target(), processIdSender)) {
                return;
            }
        }

        createTab("Direct: " + processIdSender, MessageType.DIRECT, null, processIdSender);
    }

    @Override
    public void accept(UUID messageId,
                       UUID processIdSender,
                       String channel,
                       MessageType messageType,
                       String message,
                       UUID messageReplying) {
        SwingUtilities.invokeLater(() -> {
            // this method gets called if there is a message incoming from any of our child processes
            messages.add(new MessageInfo(messageId, processIdSender, channel, messageType, message, messageReplying));
            if (messages.size() > MAX_MESSAGES) {
                messages.removeFirst();
            }

            if (messageType == MessageType.DIRECT) { // special case for DMs, auto create a tab for that...
                createTabForDM(processIdSender);
            }

            chatLog(messageType,
                    channel,
                    messageType == MessageType.DIRECT ? processManager.getMyId() : null,
                    processIdSender,
                    messageId.toString(),
                    message,
                    messageReplying);

            playReceiveSound();
        });
    }

    private void playSound(byte[] sound) {
        try {
            Clip clip = AudioSystem.getClip();
            clip.open(SOUND_FORMAT, sound, 0, sound.length);
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    clip.close();
                }
            });
            clip.start();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }

    public void playReceiveSound() {
        if (!enableSoundsCheckbox.isSelected()) {
            return;
        }

        if (receiveSound != null) {
            playSound(receiveSound);
        }
    }

    public void playSendSound() {
        if (!enableSoundsCheckbox.isSelected()) {
            return;
        }

        if (sendSound != null) {
            playSound(sendSound);
        }
    }
}
