; Script generated by the Inno Setup Script Wizard.
; SEE THE DOCUMENTATION FOR DETAILS ON CREATING INNO SETUP SCRIPT FILES!

#define MyAppName "Label Print Agent"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "Sharptree LLC"
#define MyAppURL "https://github.com/sharptree/label-print-agent"

[Setup]
AppId={{E100A12F-D50A-4463-A777-E204FF0B1A35}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={commonpf64}\LabelPrintAgent
DefaultGroupName={#MyAppName}
; Uncomment the following line to run in non administrative install mode (install for current user only.)
;PrivilegesRequired=lowest
OutputBaseFilename=label-print-agent-setup

SetupIconFile=sharptree.ico
Compression=lzma
SolidCompression=yes
WizardStyle=modern
WizardImageFile=wizard_sharptree.bmp
WizardSmallImageFile=sharptree.bmp

SetupLogging=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Files]
Source: "label-print-agent-install-service.bat"; DestDir: "{app}"; Flags: ignoreversion
Source: "label-print-agent-uninstall-service.bat"; DestDir: "{app}"; Flags: ignoreversion
Source: "LICENSE.txt"; DestDir: "{app}"; Flags: ignoreversion
Source: "logback.xml"; DestDir: "{app}"; Flags: ignoreversion
Source: "NOTICE.txt"; DestDir: "{app}"; Flags: ignoreversion
Source: "label-print-agentw.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "label-print-agent.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "label-print-agent.jar"; DestDir: "{app}"; Flags: ignoreversion
Source: "label-print-agent-cli.bat"; DestDir: "{app}"; Flags: ignoreversion
Source: "label-print-agent.yaml"; DestDir: "{app}"; Flags: ignoreversion

[UninstallDelete]
Type: filesandordirs; Name: "{app}\logs"

[Run]
Filename: "{app}\label-print-agent-install-service.bat"; Description: "Install the Label Print Agent Windows Service"; Flags: postinstall runhidden waituntilterminated  runascurrentuser

[UninstallRun]
Filename: "{app}\label-print-agent-install-service.bat";RunOnceId:"DeleteService";  Flags:  runhidden waituntilterminated runascurrentuser
