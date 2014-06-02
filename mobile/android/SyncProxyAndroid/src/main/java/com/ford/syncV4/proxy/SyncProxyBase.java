package com.ford.syncV4.proxy;

import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;

import com.ford.syncV4.exception.SyncException;
import com.ford.syncV4.exception.SyncExceptionCause;
import com.ford.syncV4.marshal.IJsonRPCMarshaller;
import com.ford.syncV4.marshal.JsonRPCMarshaller;
import com.ford.syncV4.messageDispatcher.IDispatchingStrategy;
import com.ford.syncV4.messageDispatcher.IncomingProtocolMessageComparator;
import com.ford.syncV4.messageDispatcher.InternalProxyMessageComparator;
import com.ford.syncV4.messageDispatcher.OutgoingProtocolMessageComparator;
import com.ford.syncV4.messageDispatcher.ProxyMessageDispatcher;
import com.ford.syncV4.net.SyncPDataSender;
import com.ford.syncV4.protocol.ProtocolMessage;
import com.ford.syncV4.protocol.enums.FunctionID;
import com.ford.syncV4.protocol.enums.ServiceType;
import com.ford.syncV4.protocol.heartbeat.HeartbeatBuilder;
import com.ford.syncV4.proxy.callbacks.InternalProxyMessage;
import com.ford.syncV4.proxy.callbacks.OnError;
import com.ford.syncV4.proxy.callbacks.OnProxyClosed;
import com.ford.syncV4.proxy.constants.Names;
import com.ford.syncV4.proxy.constants.ProtocolConstants;
import com.ford.syncV4.proxy.converter.IRPCRequestConverter;
import com.ford.syncV4.proxy.converter.IRPCRequestConverterFactory;
import com.ford.syncV4.proxy.converter.SyncRPCRequestConverterFactory;
import com.ford.syncV4.proxy.interfaces.IProxyListenerALMTesting;
import com.ford.syncV4.proxy.interfaces.IProxyListenerBase;
import com.ford.syncV4.proxy.rpc.AddCommand;
import com.ford.syncV4.proxy.rpc.AddSubMenu;
import com.ford.syncV4.proxy.rpc.Alert;
import com.ford.syncV4.proxy.rpc.ButtonCapabilities;
import com.ford.syncV4.proxy.rpc.Choice;
import com.ford.syncV4.proxy.rpc.CreateInteractionChoiceSet;
import com.ford.syncV4.proxy.rpc.DeleteCommand;
import com.ford.syncV4.proxy.rpc.DeleteInteractionChoiceSet;
import com.ford.syncV4.proxy.rpc.DeleteSubMenu;
import com.ford.syncV4.proxy.rpc.DeviceInfo;
import com.ford.syncV4.proxy.rpc.DisplayCapabilities;
import com.ford.syncV4.proxy.rpc.EncodedSyncPData;
import com.ford.syncV4.proxy.rpc.ListFiles;
import com.ford.syncV4.proxy.rpc.OnAppInterfaceUnregistered;
import com.ford.syncV4.proxy.rpc.OnLanguageChange;
import com.ford.syncV4.proxy.rpc.OnSystemRequest;
import com.ford.syncV4.proxy.rpc.PerformInteraction;
import com.ford.syncV4.proxy.rpc.PresetBankCapabilities;
import com.ford.syncV4.proxy.rpc.PutFile;
import com.ford.syncV4.proxy.rpc.PutFileResponse;
import com.ford.syncV4.proxy.rpc.RegisterAppInterface;
import com.ford.syncV4.proxy.rpc.RegisterAppInterfaceResponse;
import com.ford.syncV4.proxy.rpc.ResetGlobalProperties;
import com.ford.syncV4.proxy.rpc.SetAppIcon;
import com.ford.syncV4.proxy.rpc.SetGlobalProperties;
import com.ford.syncV4.proxy.rpc.SetMediaClockTimer;
import com.ford.syncV4.proxy.rpc.Show;
import com.ford.syncV4.proxy.rpc.SoftButtonCapabilities;
import com.ford.syncV4.proxy.rpc.Speak;
import com.ford.syncV4.proxy.rpc.SubscribeButton;
import com.ford.syncV4.proxy.rpc.SyncMsgVersion;
import com.ford.syncV4.proxy.rpc.SyncPData;
import com.ford.syncV4.proxy.rpc.SystemRequest;
import com.ford.syncV4.proxy.rpc.TTSChunk;
import com.ford.syncV4.proxy.rpc.UnregisterAppInterface;
import com.ford.syncV4.proxy.rpc.UnregisterAppInterfaceResponse;
import com.ford.syncV4.proxy.rpc.UnsubscribeButton;
import com.ford.syncV4.proxy.rpc.VehicleType;
import com.ford.syncV4.proxy.rpc.enums.AppHMIType;
import com.ford.syncV4.proxy.rpc.enums.AppInterfaceUnregisteredReason;
import com.ford.syncV4.proxy.rpc.enums.AudioStreamingState;
import com.ford.syncV4.proxy.rpc.enums.ButtonName;
import com.ford.syncV4.proxy.rpc.enums.FileType;
import com.ford.syncV4.proxy.rpc.enums.GlobalProperty;
import com.ford.syncV4.proxy.rpc.enums.HMILevel;
import com.ford.syncV4.proxy.rpc.enums.HmiZoneCapabilities;
import com.ford.syncV4.proxy.rpc.enums.InteractionMode;
import com.ford.syncV4.proxy.rpc.enums.Language;
import com.ford.syncV4.proxy.rpc.enums.RequestType;
import com.ford.syncV4.proxy.rpc.enums.Result;
import com.ford.syncV4.proxy.rpc.enums.SpeechCapabilities;
import com.ford.syncV4.proxy.rpc.enums.SyncConnectionState;
import com.ford.syncV4.proxy.rpc.enums.SyncDisconnectedReason;
import com.ford.syncV4.proxy.rpc.enums.TextAlignment;
import com.ford.syncV4.proxy.rpc.enums.UpdateMode;
import com.ford.syncV4.proxy.rpc.enums.VrCapabilities;
import com.ford.syncV4.proxy.systemrequest.IOnSystemRequestHandler;
import com.ford.syncV4.proxy.systemrequest.ISystemRequestProxy;
import com.ford.syncV4.service.Service;
import com.ford.syncV4.session.Session;
import com.ford.syncV4.syncConnection.ISyncConnectionListener;
import com.ford.syncV4.syncConnection.SyncConnection;
import com.ford.syncV4.test.ITestConfigCallback;
import com.ford.syncV4.test.TestConfig;
import com.ford.syncV4.trace.TraceDeviceInfo;
import com.ford.syncV4.transport.BaseTransportConfig;
import com.ford.syncV4.transport.TransportType;
import com.ford.syncV4.util.CommonUtils;
import com.ford.syncV4.util.DeviceInfoManager;
import com.ford.syncV4.util.logger.Logger;

import java.io.OutputStream;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

public abstract class SyncProxyBase<proxyListenerType extends IProxyListenerBase> implements
        ISystemRequestProxy {

    @SuppressWarnings("unused")
    private static final String LOG_TAG = SyncProxyBase.class.getSimpleName();

    // Synchronization Objects
    static final Object CONNECTION_REFERENCE_LOCK = new Object(),
            INCOMING_MESSAGE_QUEUE_THREAD_LOCK = new Object(),
            OUTGOING_MESSAGE_QUEUE_THREAD_LOCK = new Object(),
            INTERNAL_MESSAGE_QUEUE_THREAD_LOCK = new Object(),
            APP_INTERFACE_REGISTERED_LOCK = new Object();
    /**
     * Interval between heartbeat messages, in milliseconds.
     * NOTE: this value is not specified in the protocol, and thus must be
     * negotiated with the Sync.
     */
    static final int HEARTBEAT_INTERVAL = 5000;

    /** Delay between proxy disconnect (e.g., transport error) and another proxy
     * reconnect attempt.
     */
    private static final int PROXY_RECONNECT_DELAY = 5000;
    /**
     * Lock to access the _currentReconnectTimerTask member.
     */
    private static final Object RECONNECT_TIMER_TASK_LOCK = new Object();

    final int HEARTBEAT_CORRELATION_ID = 65531; // TODO: remove

    // Protected Correlation IDs
    private static final int REGISTER_APP_INTERFACE_CORRELATION_ID = 65529;
    private static final int UNREGISTER_APP_INTERFACE_CORRELATION_ID = 65530;
    private static final int POLICIES_CORRELATION_ID = 65535;

    private IRPCMessageHandler rpcMessageHandler;

    private int mRegisterAppInterfaceCorrelationId = REGISTER_APP_INTERFACE_CORRELATION_ID;
    private int mUnregisterAppInterfaceCorrelationId = UNREGISTER_APP_INTERFACE_CORRELATION_ID;
    private int mPoliciesCorrelationId = POLICIES_CORRELATION_ID;

    public Boolean getAdvancedLifecycleManagementEnabled() {
        return _advancedLifecycleManagementEnabled;
    }

    // SyncProxy Advanced Lifecycle Management
    protected Boolean _advancedLifecycleManagementEnabled = false;

    protected Hashtable<String, Boolean> mAppInterfaceRegistered = new Hashtable<String, Boolean>();

    // These variables are not used
    //protected Boolean _haveReceivedFirstFocusLevel = false;
    //protected Boolean _haveReceivedFirstFocusLevelFull = false;
    //protected SyncInterfaceAvailability _syncIntefaceAvailablity = null;
    //Boolean _haveReceivedFirstNonNoneHMILevel = false;

    protected Boolean _proxyDisposed = false;

    public SyncConnectionState getSyncConnectionState() {
        return _syncConnectionState;
    }

    public void setSyncConnectionState(SyncConnectionState syncConnectionState) {
        this._syncConnectionState = syncConnectionState;
    }

    protected SyncConnectionState _syncConnectionState = null;

    public SyncMsgVersion getSyncMsgVersion() throws SyncException {
        return _syncMsgVersion;
    }

    public void setSyncMsgVersion(SyncMsgVersion syncMsgVersion) {
        this._syncMsgVersion = syncMsgVersion;
    }

    // Variables set by RegisterAppInterfaceResponse
    protected SyncMsgVersion _syncMsgVersion = null;
    protected String _autoActivateIdReturned = null;

    public Language getSyncLanguage() throws SyncException {
        return _syncLanguage;
    }

    public void setSyncLanguage(Language syncLanguage) {
        this._syncLanguage = syncLanguage;
    }

    protected Language _syncLanguage = null;

    public Language getHmiDisplayLanguage() throws SyncException {
        return _hmiDisplayLanguage;
    }

    public void setHmiDisplayLanguage(Language hmiDisplayLanguage) {
        this._hmiDisplayLanguage = hmiDisplayLanguage;
    }

    protected Language _hmiDisplayLanguage = null;

    public DisplayCapabilities getDisplayCapabilities() throws SyncException {
        return _displayCapabilities;
    }

    public void setDisplayCapabilities(DisplayCapabilities displayCapabilities) {
        this._displayCapabilities = displayCapabilities;
    }

    protected DisplayCapabilities _displayCapabilities = null;

    public Vector<ButtonCapabilities> getButtonCapabilities() throws SyncException {
        return _buttonCapabilities;
    }

    public void setButtonCapabilities(Vector<ButtonCapabilities> buttonCapabilities) {
        this._buttonCapabilities = buttonCapabilities;
    }

    protected Vector<ButtonCapabilities> _buttonCapabilities = null;

    public Vector<SoftButtonCapabilities> getSoftButtonCapabilities() throws SyncException {
        return _softButtonCapabilities;
    }

    public void setSoftButtonCapabilities(Vector<SoftButtonCapabilities> softButtonCapabilities) {
        this._softButtonCapabilities = softButtonCapabilities;
    }

    protected Vector<SoftButtonCapabilities> _softButtonCapabilities = null;

    public PresetBankCapabilities getPresetBankCapabilities() throws SyncException {
        return _presetBankCapabilities;
    }

    public void setPresetBankCapabilities(PresetBankCapabilities presetBankCapabilities) {
        this._presetBankCapabilities = presetBankCapabilities;
    }

    protected PresetBankCapabilities _presetBankCapabilities = null;

    public Vector<HmiZoneCapabilities> getHmiZoneCapabilities() throws SyncException {
        return _hmiZoneCapabilities;
    }

    public void setHmiZoneCapabilities(Vector<HmiZoneCapabilities> hmiZoneCapabilities) {
        this._hmiZoneCapabilities = hmiZoneCapabilities;
    }

    protected Vector<HmiZoneCapabilities> _hmiZoneCapabilities = null;

    public Vector<SpeechCapabilities> getSpeechCapabilities() throws SyncException {
        return _speechCapabilities;
    }

    public void setSpeechCapabilities(Vector<SpeechCapabilities> speechCapabilities) {
        this._speechCapabilities = speechCapabilities;
    }

    protected Vector<SpeechCapabilities> _speechCapabilities = null;

    public Vector<VrCapabilities> getVrCapabilities() throws SyncException {
        return _vrCapabilities;
    }

    public void setVrCapabilities(Vector<VrCapabilities> vrCapabilities) {
        this._vrCapabilities = vrCapabilities;
    }

    protected Vector<VrCapabilities> _vrCapabilities = null;

    public VehicleType getVehicleType() throws SyncException {
        return _vehicleType;
    }

    public void setVehicleType(VehicleType vehicleType) {
        this._vehicleType = vehicleType;
    }

    protected VehicleType _vehicleType = null;

    public Boolean getFirstTimeFull() {
        return firstTimeFull;
    }

    public void setFirstTimeFull(Boolean firstTimeFull) {
        this.firstTimeFull = firstTimeFull;
    }

    protected Boolean firstTimeFull = true;

    SyncConnection mSyncConnection;

    /**
     * Keep track of all opened Sync Sessions. Need to be "protected" in order to support
     * UnitTesting
     */
    protected final Session syncSession = new Session();

    public proxyListenerType getProxyListener() {
        return _proxyListener;
    }

    private proxyListenerType _proxyListener = null;
    // Device Info for logging
    private TraceDeviceInfo mTraceDeviceInterrogator = null;
    // Declare Queuing Threads
    private ProxyMessageDispatcher<ProtocolMessage> _incomingProxyMessageDispatcher;
    private ProxyMessageDispatcher<ProtocolMessage> _outgoingProxyMessageDispatcher;
    private ProxyMessageDispatcher<InternalProxyMessage> _internalProxyMessageDispatcher;

    public boolean getCallbackToUIThread() {
        return _callbackToUIThread;
    }

    public void setCallbackToUIThread(boolean callbackToUIThread) {
        this._callbackToUIThread = callbackToUIThread;
    }

    // Flag indicating if callbacks should be called from UIThread
    private boolean _callbackToUIThread = false;

    public Handler getMainUIHandler() {
        return _mainUIHandler;
    }

    public void setMainUIHandler(Handler mainUIHandler) {
        this._mainUIHandler = mainUIHandler;
    }

    // UI Handler
    private Handler _mainUIHandler = null;

    private final HashSet<String> appIds = new HashSet<String>();
    private final Hashtable<String, RegisterAppInterface> raiTable =
            new Hashtable<String, RegisterAppInterface>();

    // Updated hashID which can be used over connection cycles
    // (i.e. loss of connection, ignition cycles, etc.)
    // Key is AppId
    private final Hashtable<String, String> hashIdsTable = new Hashtable<String, String>();

    /**
     * Describes information about device
     */
    private DeviceInfo mDeviceInfo;

    /**
     * Test Cases fields
     */
    /**
     * This Config object stores all the necessary data for SDK testing
     */
    private TestConfig mTestConfig;
    /**
     * This is a callback interface for the SDK test cases usage
     */
    private ITestConfigCallback mTestConfigCallback;

    /**
     * Set hashID which can be used over connection cycles
     *
     * @param appId Application identifier
     * @return value of the hashId
     */
    public String getHashId(String appId) {
        // For the Test Cases
        if (mTestConfig != null) {
            if (!mTestConfig.isUseHashId()) {
                return null;
            }
            if (mTestConfig.isUseCustomHashId()) {
                return mTestConfig.getCustomHashId();
            }
        }

        return hashIdsTable.get(appId);
    }

    public void setHashId(byte sessionId, String value) {
        String appId = syncSession.getAppIdBySessionId(sessionId);
        setHashId(appId, value);
    }

    /**
     * Get hashId which can be used over connection cycles
     *
     * @param appId Application identifier
     * @param value value of the hashId
     */
    public void setHashId(String appId, String value) {
        if (value == null) {
            if (hashIdsTable.containsKey(appId)) {
                hashIdsTable.remove(appId);
                return;
            }
            return;
        }
        hashIdsTable.put(appId, value);
    }

    protected DeviceInfo getDeviceInfo() {
        return mDeviceInfo;
    }

    private void setDeviceInfo(DeviceInfo deviceInfo) {
        mDeviceInfo = deviceInfo;
    }

    public OnLanguageChange getLastLanguageChange() {
        return _lastLanguageChange;
    }

    public void setLastLanguageChange(OnLanguageChange lastLanguageChange) {
        this._lastLanguageChange = lastLanguageChange;
    }

    private OnLanguageChange _lastLanguageChange = null;
    // JSON RPC Marshaller
    private IJsonRPCMarshaller _jsonRPCMarshaller = new JsonRPCMarshaller();
    /**
     * Contains current configuration for the transport that was selected during
     * construction of this object
     */
    private BaseTransportConfig _transportConfig = null;

    public HMILevel getPriorHmiLevel() {
        return _priorHmiLevel;
    }

    public void setPriorHmiLevel(HMILevel priorHmiLevel) {
        this._priorHmiLevel = priorHmiLevel;
    }

    private HMILevel _priorHmiLevel = null;

    public AudioStreamingState getPriorAudioStreamingState() {
        return _priorAudioStreamingState;
    }

    public void setPriorAudioStreamingState(AudioStreamingState priorAudioStreamingState) {
        this._priorAudioStreamingState = priorAudioStreamingState;
    }

    private AudioStreamingState _priorAudioStreamingState = null;
    // Interface broker
    private SyncInterfaceBroker _interfaceBroker = null;
    /**
     * Timer that is used to schedule proxy reconnect tasks.
     */
    private Timer _reconnectTimer = null;
    /**
     * Currently scheduled proxy reconnect task, if any.
     */
    private TimerTask _currentReconnectTimerTask = null;
    private static int heartBeatInterval = HEARTBEAT_INTERVAL;
    private static boolean heartBeatAck = true;
    private IRPCRequestConverterFactory rpcRequestConverterFactory =
            new SyncRPCRequestConverterFactory();
    private IProtocolMessageHolder protocolMessageHolder =
            new ProtocolMessageHolder();

    /**
     * Handler for OnSystemRequest notifications.
     */
    private IOnSystemRequestHandler onSystemRequestHandler;

    /**
     * A set of internal requests' correlation IDs that are currently in
     * progress.
     */
    private Set<Integer> internalRequestCorrelationIDs;

    /**
     * Correlation ID that was last used for messages created internally.
     */
    private int lastCorrelationId = 40000;

    public void setSyncConnection(SyncConnection syncConnection) {
        this.mSyncConnection = syncConnection;
    }

    /**
     * Constructor.
     *
     * @param listener                          Type of listener for this proxy base.
     * @param syncProxyConfigurationResources   Configuration resources for this proxy.
     * @param enableAdvancedLifecycleManagement Flag that ALM should be enabled or not.
     * @param appName                           Client application name.
     * @param ttsName                           TTS name.
     * @param ngnMediaScreenAppName             Media Screen Application name.
     * @param vrSynonyms                        List of synonyms.
     * @param isMediaApp                        Flag that indicates that client application if media application or not.
     * @param syncMsgVersion                    Version of Sync Message.
     * @param languageDesired                   Desired language.
     * @param hmiDisplayLanguageDesired         Desired language for HMI.
     * @param appHMIType                        Type of application.
     * @param appID                             Application identifier.
     * @param autoActivateID                    Auto activation identifier.
     * @param callbackToUIThread                Flag that indicates that this proxy should send callback to UI thread or not.
     * @param transportConfig                   Configuration of transport to be used by underlying connection.
     * @throws SyncException
     */
    protected SyncProxyBase(proxyListenerType listener, SyncProxyConfigurationResources syncProxyConfigurationResources,
                            boolean enableAdvancedLifecycleManagement, String appName, Vector<TTSChunk> ttsName,
                            String ngnMediaScreenAppName, Vector<String> vrSynonyms, Boolean isMediaApp, SyncMsgVersion syncMsgVersion,
                            Language languageDesired, Language hmiDisplayLanguageDesired, Vector<AppHMIType> appHMIType, String appID,
                            String autoActivateID, boolean callbackToUIThread, BaseTransportConfig transportConfig, TestConfig testConfig)
            throws SyncException {

        mTestConfig = testConfig;

        setupSyncProxyBaseComponents(callbackToUIThread);
        // Set variables for Advanced Lifecycle Management
        setAdvancedLifecycleManagementEnabled(enableAdvancedLifecycleManagement);
        updateRegisterAppInterfaceParameters(appName, ttsName, ngnMediaScreenAppName, vrSynonyms, isMediaApp, syncMsgVersion, languageDesired, hmiDisplayLanguageDesired, appHMIType, appID, autoActivateID);
        setTransportConfig(transportConfig);
        checkConditionsInvalidateProxy(listener);
        setProxyListener(listener);
        // Get information from syncProxyConfigurationResources
        setupTelephoneManager(syncProxyConfigurationResources);
        setupMessageDispatchers();
        tryInitialiseProxy();

        mDeviceInfo = DeviceInfoManager.getDeviceInfo(syncProxyConfigurationResources.getTelephonyManager());

        // Trace that ctor has fired
        Logger.i("SyncProxy Created, instanceID=" + this.toString());
    }

    /**
     * Constructor.
     *
     * @param listener                          Type of listener for this proxy base.
     * @param syncProxyConfigurationResources   Configuration resources for this proxy.
     * @param enableAdvancedLifecycleManagement Flag that ALM should be enabled or not.
     * @param appName                           Client application name.
     * @param ttsName                           TTS name.
     * @param ngnMediaScreenAppName             Media Screen Application name.
     * @param vrSynonyms                        List of synonyms.
     * @param isMediaApp                        Flag that indicates that client application if media application or not.
     * @param syncMsgVersion                    Version of Sync Message.
     * @param languageDesired                   Desired language.
     * @param hmiDisplayLanguageDesired         Desired language for HMI.
     * @param appHMIType                        Type of application.
     * @param appId                             Application identifier.
     * @param autoActivateID                    Auto activation identifier.
     * @param callbackToUIThread                Flag that indicates that this proxy should send callback to UI thread or not.
     * @param preRegister                       Flag that indicates that this proxy should be pre-registerd or not.
     * @param version                           Version of Sync protocol to be used by the underlying connection.
     * @param transportConfig                   Configuration of transport to be used by underlying connection.
     * @throws SyncException
     */
    protected SyncProxyBase(proxyListenerType listener, SyncProxyConfigurationResources syncProxyConfigurationResources,
                            boolean enableAdvancedLifecycleManagement, String appName, Vector<TTSChunk> ttsName,
                            String ngnMediaScreenAppName, Vector<String> vrSynonyms, Boolean isMediaApp, SyncMsgVersion syncMsgVersion,
                            Language languageDesired, Language hmiDisplayLanguageDesired, Vector<AppHMIType> appHMIType, String appId,
                            String autoActivateID, boolean callbackToUIThread, boolean preRegister, int version,
                            BaseTransportConfig transportConfig, SyncConnection connection, TestConfig testConfig)
            throws SyncException {

        mTestConfig = testConfig;

        setAppInterfacePreRegistered(appId, preRegister);

        setupSyncProxyBaseComponents(callbackToUIThread);

        // Set variables for Advanced Lifecycle Management
        setAdvancedLifecycleManagementEnabled(enableAdvancedLifecycleManagement);
        updateRegisterAppInterfaceParameters(appName, ttsName, ngnMediaScreenAppName, vrSynonyms,
                isMediaApp, syncMsgVersion, languageDesired, hmiDisplayLanguageDesired, appHMIType,
                appId, autoActivateID);
        setTransportConfig(transportConfig);

        // Test conditions to invalidate the proxy
        checkConditionsInvalidateProxy(listener);

        setProxyListener(listener);
        setSyncConnection(connection);

        setupTelephoneManager(syncProxyConfigurationResources);

        setupMessageDispatchers();
        tryInitialiseProxy();

        if (syncProxyConfigurationResources != null) {
            mDeviceInfo = DeviceInfoManager.getDeviceInfo(syncProxyConfigurationResources.getTelephonyManager());
        }

        // Trace that ctor has fired
        Logger.i("SyncProxy Created, instanceID=" + this.toString());
    }

    public void updateRegisterAppInterfaceParameters(RegisterAppInterface registerAppInterface) {
        if (registerAppInterface.getCorrelationID() != null) {
            setRegisterAppInterfaceCorrelationId(registerAppInterface.getCorrelationID());
        }
        if (registerAppInterface.getDeviceInfo() != null) {
            setDeviceInfo(registerAppInterface.getDeviceInfo());
            DeviceInfoManager.copyDeviceInfo(getDeviceInfo(), registerAppInterface.getDeviceInfo());
        }

        RegisterAppInterface registerAppInterfaceToUpdate = raiTable.get(registerAppInterface.getAppID());
        if (registerAppInterfaceToUpdate == null) {
            registerAppInterfaceToUpdate = RPCRequestFactory.buildRegisterAppInterface();
            raiTable.put((String) registerAppInterface.getAppID(), registerAppInterface);
        }
        if (registerAppInterfaceToUpdate != null) {
            registerAppInterfaceToUpdate.setTtsName(registerAppInterface.getTtsName());
            registerAppInterfaceToUpdate.setNgnMediaScreenAppName(registerAppInterface.getNgnMediaScreenAppName());
            registerAppInterfaceToUpdate.setIsMediaApplication(registerAppInterface.getIsMediaApplication());
            registerAppInterfaceToUpdate.setVrSynonyms(registerAppInterface.getVrSynonyms());
            registerAppInterfaceToUpdate.setSyncMsgVersion(registerAppInterface.getSyncMsgVersion());
            registerAppInterfaceToUpdate.setLanguageDesired(registerAppInterface.getLanguageDesired());
            registerAppInterfaceToUpdate.setHmiDisplayLanguageDesired(registerAppInterface.getHmiDisplayLanguageDesired());
            registerAppInterfaceToUpdate.setAppName(registerAppInterface.getAppName());
            registerAppInterfaceToUpdate.setAppId(registerAppInterface.getAppID());

            if (registerAppInterfaceToUpdate.getCorrelationID() != null) {
                setRegisterAppInterfaceCorrelationId(registerAppInterfaceToUpdate.getCorrelationID());
            }
            if (registerAppInterfaceToUpdate.getDeviceInfo() != null) {
                setDeviceInfo(registerAppInterfaceToUpdate.getDeviceInfo());
                DeviceInfoManager.copyDeviceInfo(getDeviceInfo(), registerAppInterfaceToUpdate.getDeviceInfo());
            }
        }
    }

    private void updateRegisterAppInterfaceParameters(String appName, Vector<TTSChunk> ttsName,
                                                      String ngnMediaScreenAppName,
                                                      Vector<String> vrSynonyms, Boolean isMediaApp,
                                                      SyncMsgVersion syncMsgVersion,
                                                      Language languageDesired,
                                                      Language hmiDisplayLanguageDesired,
                                                      Vector<AppHMIType> appHMIType, String appId,
                                                      String autoActivateID) {
        appIds.add(appId);

        RegisterAppInterface registerAppInterface = raiTable.get(appId);
        if (registerAppInterface == null) {
            registerAppInterface = RPCRequestFactory.buildRegisterAppInterface();
            raiTable.put(appId, registerAppInterface);
        }
        if (registerAppInterface != null) {
            registerAppInterface.setAppName(appName);
            registerAppInterface.setTtsName(ttsName);
            registerAppInterface.setNgnMediaScreenAppName(ngnMediaScreenAppName);
            registerAppInterface.setIsMediaApplication(isMediaApp);
            registerAppInterface.setVrSynonyms(vrSynonyms);
            registerAppInterface.setSyncMsgVersion(syncMsgVersion);
            registerAppInterface.setLanguageDesired(languageDesired);
            registerAppInterface.setHmiDisplayLanguageDesired(hmiDisplayLanguageDesired);
            registerAppInterface.setAppType(appHMIType);
            registerAppInterface.setAppId(appId);
        }
    }

    private void setupMessageDispatchers() {
        setupInternalProxyMessageDispatcher();
        setupIncomingProxyMessageDispatcher();
        setupOutgoingMessageDispatcher();
    }

    private void setTransportConfig(BaseTransportConfig transportConfig) {
        _transportConfig = transportConfig;
    }

    private void setAdvancedLifecycleManagementEnabled(boolean enableAdvancedLifecycleManagement) {
        _advancedLifecycleManagementEnabled = enableAdvancedLifecycleManagement;
    }

    private void setProxyListener(proxyListenerType listener) {
        _proxyListener = listener;
    }

    protected void setAppInterfacePreRegistered(String appId, boolean preRegister) {
        if (preRegister) {
            mAppInterfaceRegistered.put(appId, preRegister);
        }
    }

    protected void setAppInterfaceRegistered(byte sessionId, boolean registered) {
        String appId = syncSession.getAppIdBySessionId(sessionId);
        mAppInterfaceRegistered.put(appId, registered);
    }

    private void setupSyncProxyBaseComponents(boolean callbackToUIThread) {
        _interfaceBroker = new SyncInterfaceBroker();

        _callbackToUIThread = callbackToUIThread;

        if (_callbackToUIThread) {
            _mainUIHandler = new Handler(Looper.getMainLooper());
        }

        rpcMessageHandler = new RPCMessageHandler(this);

    }

    private void checkConditionsInvalidateProxy(proxyListenerType listener) {
        // TODO : Maybe ...
    }

    private void setupOutgoingMessageDispatcher() {
        // Setup Outgoing ProxyMessage Dispatcher
        //synchronized (OUTGOING_MESSAGE_QUEUE_THREAD_LOCK) {
            // Ensure outgoingProxyMessageDispatcher is null
            if (_outgoingProxyMessageDispatcher != null) {
                _outgoingProxyMessageDispatcher.dispose();
                _outgoingProxyMessageDispatcher = null;
            }

            _outgoingProxyMessageDispatcher = new ProxyMessageDispatcher<ProtocolMessage>("OUTGOING_MESSAGE_DISPATCHER",
                    new OutgoingProtocolMessageComparator(),
                    new IDispatchingStrategy<ProtocolMessage>() {
                        @Override
                        public void dispatch(ProtocolMessage message) {
                            dispatchOutgoingMessage(message);
                        }

                        @Override
                        public void handleDispatchingError(String info, Exception ex) {
                            handleErrorsFromOutgoingMessageDispatcher(info, ex);
                        }

                        @Override
                        public void handleQueueingError(String info, Exception ex) {
                            handleErrorsFromOutgoingMessageDispatcher(info, ex);
                        }
                    });
        //}
    }

    private void setupInternalProxyMessageDispatcher() {
        // Setup Internal ProxyMessage Dispatcher
        synchronized (INTERNAL_MESSAGE_QUEUE_THREAD_LOCK) {
            // Ensure internalProxyMessageDispatcher is null
            if (_internalProxyMessageDispatcher != null) {
                _internalProxyMessageDispatcher.dispose();
                _internalProxyMessageDispatcher = null;
            }

            _internalProxyMessageDispatcher = new ProxyMessageDispatcher<InternalProxyMessage>("INTERNAL_MESSAGE_DISPATCHER",
                    new InternalProxyMessageComparator(),
                    new IDispatchingStrategy<InternalProxyMessage>() {

                        @Override
                        public void dispatch(InternalProxyMessage message) {
                            dispatchInternalMessage(message);
                        }

                        @Override
                        public void handleDispatchingError(String info, Exception ex) {
                            handleErrorsFromInternalMessageDispatcher(info, ex);
                        }

                        @Override
                        public void handleQueueingError(String info, Exception ex) {
                            handleErrorsFromInternalMessageDispatcher(info, ex);
                        }
                    });
        }
    }

    private void setupTelephoneManager(SyncProxyConfigurationResources syncProxyConfigurationResources) {
        // Get information from syncProxyConfigurationResources
        TelephonyManager telephonyManager = null;
        if (syncProxyConfigurationResources != null) {
            telephonyManager = syncProxyConfigurationResources.getTelephonyManager();
        }

        // Use the telephonyManager to get and log phone info
        if (telephonyManager != null) {
            // Following is not quite thread-safe (because m_traceLogger could notifyOnAppInterfaceUnregistered null twice),
            // so we need to fix this, but vulnerability (i.e. two instances of listener) is
            // likely harmless.
            if (mTraceDeviceInterrogator == null) {
                mTraceDeviceInterrogator = new TraceDeviceInfo(telephonyManager);
            }
        }
    }

    private void tryInitialiseProxy() throws SyncException {
        // Initialize the proxy
        try {
            initializeProxy();
        } catch (SyncException e) {
            // Couldn't initialize the proxy
            // Dispose threads and then rethrow exception

            if (_internalProxyMessageDispatcher != null) {
                _internalProxyMessageDispatcher.dispose();
                _internalProxyMessageDispatcher = null;
            }
            if (_incomingProxyMessageDispatcher != null) {
                _incomingProxyMessageDispatcher.dispose();
                _incomingProxyMessageDispatcher = null;
            }
            if (_outgoingProxyMessageDispatcher != null) {
                _outgoingProxyMessageDispatcher.dispose();
                _outgoingProxyMessageDispatcher = null;
            }
            throw e;
        }
    }

    private void setupIncomingProxyMessageDispatcher() {
        // Setup Incoming ProxyMessage Dispatcher
        synchronized (INCOMING_MESSAGE_QUEUE_THREAD_LOCK) {
            // Ensure incomingProxyMessageDispatcher is null
            if (_incomingProxyMessageDispatcher != null) {
                _incomingProxyMessageDispatcher.dispose();
                _incomingProxyMessageDispatcher = null;
            }

            _incomingProxyMessageDispatcher = new ProxyMessageDispatcher<ProtocolMessage>("INCOMING_MESSAGE_DISPATCHER",
                    new IncomingProtocolMessageComparator(),
                    new IDispatchingStrategy<ProtocolMessage>() {
                        @Override
                        public void dispatch(ProtocolMessage message) {
                            dispatchIncomingMessage((ProtocolMessage) message);
                        }

                        @Override
                        public void handleDispatchingError(String info, Exception ex) {
                            handleErrorsFromIncomingMessageDispatcher(info, ex);
                        }

                        @Override
                        public void handleQueueingError(String info, Exception ex) {
                            handleErrorsFromIncomingMessageDispatcher(info, ex);
                        }
                    });
        }
    }

    // Public method to enable the siphon transport
    /*public static void enableSiphonDebug() {
        SiphonServer.enableSiphonServer();
    }*/

    // Public method to disable the Siphon Trace Server
    /*public static void disableSiphonDebug() {
        SiphonServer.disableSiphonServer();
    }*/

    public static void setHeartBeatInterval(int heartBeatInterval) {
        SyncProxyBase.heartBeatInterval = heartBeatInterval;
    }

    public static int getHeartBeatInterval() {
        return heartBeatInterval;
    }

    public SyncConnection getSyncConnection() {
        return mSyncConnection;
    }

    public ProxyMessageDispatcher<ProtocolMessage> getIncomingProxyMessageDispatcher() {
        return _incomingProxyMessageDispatcher;
    }

    public SyncInterfaceBroker getInterfaceBroker() {
        return _interfaceBroker;
    }

    public void sendEncodedSyncPDataToUrl(final String appId, String urlString,
                                          Vector<String> encodedSyncPData, Integer timeout) {
        SyncPDataSender syncPDataSender = new SyncPDataSender(getPoliciesCorrelationId());
        syncPDataSender.sendEncodedPData(urlString, encodedSyncPData, timeout,
                new SyncPDataSender.SyncPDataSenderCallback() {
                    @Override
                    public void onComplete(RPCRequest rpcRequest) {
                        if (getIsConnected()) {
                            try {
                                sendRPCRequestPrivate(appId, rpcRequest);
                                Logger.i("Encoded SyncPData sent to SDL");
                            } catch (SyncException e) {
                                Logger.i("Error sending Encoded SyncPData to SDL:" + e.getMessage());
                            }
                        }
                    }

                    @Override
                    public void onError(String message) {
                        Logger.i("Error send Encoded SyncPData to SDL:" + message);
                    }
                });
    }

    public void sendSyncPDataToUrl(final String appId, String urlString, byte[] bytes, Integer timeout) {
        SyncPDataSender syncPDataSender = new SyncPDataSender(getPoliciesCorrelationId());
        syncPDataSender.sendPData(urlString, bytes, timeout, new SyncPDataSender.SyncPDataSenderCallback() {
            @Override
            public void onComplete(RPCRequest rpcRequest) {
                if (getIsConnected()) {
                    try {
                        sendRPCRequestPrivate(appId, rpcRequest);
                        Logger.i("SyncPData sent to SDL");
                    } catch (SyncException e) {
                        Logger.i("Error sending SyncPData to SDL:" + e.getMessage());
                    }
                }
            }

            @Override
            public void onError(String message) {
                Logger.i("Error sending SyncPData to SDL:" + message);
            }
        });
    }

    // Test correlationID
    protected boolean isCorrelationIDProtected(Integer correlationID) {
        return correlationID != null &&
                (HEARTBEAT_CORRELATION_ID == correlationID
                        || getRegisterAppInterfaceCorrelationId() == correlationID
                        || getUnregisterAppInterfaceCorrelationId() == correlationID
                        || getPoliciesCorrelationId() == correlationID);

    }

    private int getRegisterAppInterfaceCorrelationId() {
        return mRegisterAppInterfaceCorrelationId;
    }

    private void setRegisterAppInterfaceCorrelationId(int registerAppInterfaceCorrelationId) {
        mRegisterAppInterfaceCorrelationId = registerAppInterfaceCorrelationId;
    }

    private int getUnregisterAppInterfaceCorrelationId() {
        return mUnregisterAppInterfaceCorrelationId;
    }

    private void setUnregisterAppInterfaceCorrelationId(int unregisterAppInterfaceCorrelationId) {
        mUnregisterAppInterfaceCorrelationId = unregisterAppInterfaceCorrelationId;
    }

    private int getPoliciesCorrelationId() {
        return mPoliciesCorrelationId;
    }

    private void setPoliciesCorrelationId(int policiesCorrelationId) {
        mPoliciesCorrelationId = policiesCorrelationId;
    }

    public boolean isRegisterAppInterfaceCorrelationIdProtected(int correlationId) {
        return mRegisterAppInterfaceCorrelationId == correlationId;
    }

    public boolean isUnregisterAppInterfaceCorrelationIdProtected(int correlationId) {
        return mUnregisterAppInterfaceCorrelationId == correlationId;
    }

    public boolean isPolicyCorrelationIdProtected(int correlationId) {
        return mPoliciesCorrelationId == correlationId;
    }

    // Protected isConnected method to allow legacy proxy to poll isConnected state
    public boolean getIsConnected() {
        if (mSyncConnection == null) {
            return false;
        }
        return mSyncConnection.getIsConnected();
    }

    /**
     * Returns whether the application is registered in SYNC. Note: for testing
     * purposes, it's possible that the connection is established, but the
     * application is not registered.
     *
     * @return true if the application is registered in SYNC
     */
    public boolean getAppInterfaceRegistered(String appId) {
        if (mAppInterfaceRegistered.get(appId) != null) {
            return mAppInterfaceRegistered.get(appId);
        }
        return false;
    }

    // This method is not used
    /*private void initState() throws SyncException {
        // Reset all of the flags and state variables
        //_haveReceivedFirstNonNoneHMILevel = false;
        //_haveReceivedFirstFocusLevel = false;
        //_haveReceivedFirstFocusLevelFull = false;
        //_syncIntefaceAvailablity = SyncInterfaceAvailability.SYNC_INTERFACE_UNAVAILABLE;
    }*/

    // Function to initialize new proxy connection
    public void initializeProxy() throws SyncException {
        //initState();

        internalRequestCorrelationIDs = new HashSet<Integer>();

        // Setup SyncConnection
        synchronized (CONNECTION_REFERENCE_LOCK) {
            if (mSyncConnection == null) {
                mSyncConnection = new SyncConnection(syncSession, _interfaceBroker);

                /**
                 * TODO : Set TestConfig for the Connection
                 */
                mSyncConnection.setTestConfig(mTestConfig);

                mSyncConnection.init(_transportConfig);
            }

            /**
             * TODO : Set TestConfig for the Connection in case we need to update it.
             * probably there is better way to do it, but as soon as this is test config
             * leave it like it is
             */
            mSyncConnection.setTestConfig(mTestConfig);

            mSyncConnection.startTransport();
        }
    }

    private synchronized void closeSyncConnection(boolean keepConnection) {
        if (mSyncConnection != null) {

            //Logger.d("Close Sync connection:" + syncSession.getSessionId() + ", " +
            //        "N:" + syncSession.getServicesNumber() + ", keep:" + keepConnection);
            //        "N:" + syncSession.getServicesNumber() + ", keep:" + keepConnection);

            mSyncConnection.closeConnection(keepConnection);

            if (!keepConnection) {
                setSyncConnection(null);
            }
        }
    }

    private void stopSession(String appId) {
        syncSession.stopSession(appId);
    }

    @Deprecated
    public void close() throws SyncException {
        dispose();
    }

    private void cleanProxy(SyncDisconnectedReason disconnectedReason,
                            boolean keepConnection) throws SyncException {
        try {
            // ALM Specific Cleanup
            if (_advancedLifecycleManagementEnabled) {
                _syncConnectionState = SyncConnectionState.SYNC_DISCONNECTED;

                firstTimeFull = true;

                Set<String> appIdToBeRemoved = new HashSet<String>(syncSession.getSessionIdsKeys());
                int counter = appIdToBeRemoved.size();
                Logger.d("Keys to remove number:" + counter);
                for (String appId: appIdToBeRemoved) {
                    closeSession(appId);
                }
                closeSyncConnection(keepConnection);
            }
        } finally {
            Logger.i("SyncProxy cleaned.");
        }
    }

    private void stopAllServicesByAppId(String appId) {
        stopMobileNaviService(appId);
        stopAudioService(appId);
    }

    /**
     * Terminates the App's Interface Registration, closes the transport connection, ends the
     * protocol syncSession, and frees any resources used by the proxy.
     */
    public void dispose() throws SyncException {
        if (_proxyDisposed) {
            throw new SyncException("This object has been disposed, it is no long capable of " +
                    "executing methods.", SyncExceptionCause.SYNC_PROXY_DISPOSED);
        }

        _proxyDisposed = true;

        Logger.i("SyncProxy start Dispose");

        try {
            // Clean the proxy
            cleanProxy(SyncDisconnectedReason.APPLICATION_REQUESTED_DISCONNECT, false);

            clearReconnectTimer();

            // Close IncomingProxyMessageDispatcher thread
            synchronized (INCOMING_MESSAGE_QUEUE_THREAD_LOCK) {
                if (_incomingProxyMessageDispatcher != null) {
                    _incomingProxyMessageDispatcher.dispose();
                    _incomingProxyMessageDispatcher = null;
                }
            }

            // Close OutgoingProxyMessageDispatcher thread
            //synchronized (OUTGOING_MESSAGE_QUEUE_THREAD_LOCK) {
                if (_outgoingProxyMessageDispatcher != null) {
                    _outgoingProxyMessageDispatcher.dispose();
                    _outgoingProxyMessageDispatcher = null;
                }
            //}

            // Close InternalProxyMessageDispatcher thread
            synchronized (INTERNAL_MESSAGE_QUEUE_THREAD_LOCK) {
                if (_internalProxyMessageDispatcher != null) {
                    _internalProxyMessageDispatcher.dispose();
                    _internalProxyMessageDispatcher = null;
                }
            }

            mTraceDeviceInterrogator = null;
        } finally {
            Logger.i("SyncProxy Disposed");
        }
    }

    // Method to cycle the proxy, only called in ALM
    protected void cycleProxy(SyncDisconnectedReason disconnectedReason) {
        Logger.d("CycleProxy, disconnectedReason:" + disconnectedReason);
        try {
            cleanProxy(disconnectedReason, true);
            scheduleInitializeProxy();
            notifyProxyClosed("Sync Proxy Cycled", new SyncException("Sync Proxy Cycled",
                    SyncExceptionCause.SYNC_PROXY_CYCLED));
        } catch (SyncException e) {
            handleCyclingSyncException(e);
        } catch (Exception e) {
            notifyProxyClosed("Cycling the proxy failed.", e);
        }
    }

    public void closeSession(String appId) throws SyncException {
        Logger.d("Close appId:" + appId);

        final byte sessionId = syncSession.getSessionIdByAppId(appId);

        // Should we wait for the interface to be unregistered?
        boolean waitForInterfaceUnregistered = false;
        synchronized (CONNECTION_REFERENCE_LOCK) {
            Boolean isAppInterfaceRegistered = mAppInterfaceRegistered.get(appId);
            if (isAppInterfaceRegistered == null) {
                isAppInterfaceRegistered = false;
            }
            if (isAppInterfaceRegistered && mSyncConnection != null &&
                    mSyncConnection.getIsConnected()) {
                waitForInterfaceUnregistered = true;

                UnregisterAppInterface unregisterAppInterface =
                        RPCRequestFactory.buildUnregisterAppInterface(
                                getUnregisterAppInterfaceCorrelationId());

                sendRPCRequestPrivate(sessionId, unregisterAppInterface);
            }
            // Wait for the app interface to be unregistered
            if (waitForInterfaceUnregistered) {
                synchronized (APP_INTERFACE_REGISTERED_LOCK) {
                    try {
                        APP_INTERFACE_REGISTERED_LOCK.wait(1000);
                    } catch (InterruptedException e) {
                        // Do nothing
                    }
                }
            }
        }
    }

    private void handleCyclingSyncException(SyncException e) {
        switch (e.getSyncExceptionCause()) {
            case BLUETOOTH_DISABLED:
                notifyProxyClosed("Bluetooth is disabled. Bluetooth must be enabled to connect to SYNC. Reattempt a connection once Bluetooth is enabled.",
                        new SyncException("Bluetooth is disabled. Bluetooth must be enabled to connect to SYNC. Reattempt a connection once Bluetooth is enabled.", SyncExceptionCause.BLUETOOTH_DISABLED));
                break;
            case BLUETOOTH_ADAPTER_NULL:
                notifyProxyClosed("Cannot locate a Bluetooth adapater. A SYNC connection is impossible on this device until a Bluetooth adapter is added.",
                        new SyncException("Cannot locate a Bluetooth adapater. A SYNC connection is impossible on this device until a Bluetooth adapter is added.", SyncExceptionCause.HEARTBEAT_PAST_DUE));
                break;
            default:
                notifyProxyClosed("Cycling the proxy failed.", e);
                break;
        }
    }

    protected void scheduleInitializeProxy() {
        Logger.d("Scheduling proxy init, services count:" + syncSession.getServicesNumber());

        if (getCurrentReconnectTimerTask() != null) {
            Logger.d("Current reconnect task is already scheduled, canceling it first");
            clearCurrentReconnectTimerTask();
        }

        TimerTask reconnectTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    Logger.d("Reconnect task is running, clearing reference");
                    setCurrentReconnectTimerTask(null);
                    initializeProxy();
                } catch (SyncException e) {
                    Logger.e("Cycling the proxy failed with SyncException.", e);
                    handleCyclingSyncException(e);
                } catch (Exception e) {
                    notifyProxyClosed("Cycling the proxy failed with Exception.", e);
                }
            }
        };
        setCurrentReconnectTimerTask(reconnectTask);

        Timer timer = getReconnectTimer();
        timer.schedule(reconnectTask, PROXY_RECONNECT_DELAY);
    }

    /**
     * Opens a syncSession, and registers the application. The connection must be
     * already opened.
     *
     * @throws SyncException if a transport connection is not opened
     */
    public void openSession() throws SyncException {
        if (!getIsConnected()) {
            throw new SyncException("Transport connection must be opened",
                    SyncExceptionCause.SYNC_CONNECTION_FAILED);
        }

        //initState();
        mSyncConnection.onTransportConnected();
    }

    /**
     * ********** Functions used by the Message Dispatching Queues ***************
     */
    protected void dispatchIncomingMessage(ProtocolMessage message) {
        try {

            //if (message.getSessionID() != syncSession.getSessionId()) {
            //    Logger.w("Message is not from current session");
            //    return;
            //}

            // Dispatching logic
            if (message.getServiceType().equals(ServiceType.RPC)) {
                try {
                    byte protocolVersion = message.getVersion();
                    Logger.i("Incoming protocol version:" + protocolVersion);
                    if (protocolVersion == ProtocolConstants.PROTOCOL_VERSION_ONE) {
                        Logger.w("Incorrect incoming protocol version, expected min 2 but was 1");
                    }

                    Hashtable hash = new Hashtable();
                    if (protocolVersion >= ProtocolConstants.PROTOCOL_VERSION_TWO) {
                        Hashtable hashTemp = new Hashtable();
                        hashTemp.put(Names.correlationID, message.getCorrID());

                        if (message.getJsonSize() > 0) {
                            final Hashtable<String, Object> mhash = _jsonRPCMarshaller.unmarshall(message.getData());
                            if (mhash != null) {
                                hashTemp.put(Names.parameters, mhash);
                            }
                        }
                        FunctionID functionID = new FunctionID();
                        String functionName = functionID.getFunctionName(message.getFunctionID());
                        if (functionName != null) {
                            hashTemp.put(Names.function_name, functionName);
                        } else {
                            Logger.e("Dispatch Incoming Message - function name is null");
                        }
                        if (message.getRPCType() == ProtocolMessage.RPCTYPE_REQUEST) {
                            hash.put(Names.request, hashTemp);
                        } else if (message.getRPCType() == ProtocolMessage.RPCTYPE_RESPONSE) {
                            hash.put(Names.response, hashTemp);
                        } else if (message.getRPCType() == ProtocolMessage.RPCTYPE_NOTIFICATION) {
                            hash.put(Names.notification, hashTemp);
                        }
                        if (message.getBulkData() != null)
                            hash.put(Names.bulkData, message.getBulkData());
                    } else {
                        final Hashtable<String, Object> mhash = _jsonRPCMarshaller.unmarshall(message.getData());
                        hash = mhash;
                    }
                    getRPCMessageHandler().handleRPCMessage(message.getSessionID(), hash);
                } catch (final Exception excp) {
                    Logger.e("Failure handling protocol message: " + excp.toString(), excp);
                    passErrorToProxyListener("Error handing incoming protocol message.", excp);
                } // end-catch
            } else {
                // Handle other protocol message types here
            }
        } catch (final Exception e) {
            // Pass error to application through listener
            Logger.e("Error handing proxy event.", e);
            passErrorToProxyListener("Error handing incoming protocol message.", e);
        }
    }

    protected void handleMobileNavMessage(ProtocolMessage message) {
        Logger.i("Mobile Nav Session message received" + message.toString());
        // TODO handle incoming mobile nav sessions
    }

    private void handleErrorsFromIncomingMessageDispatcher(String info, Exception e) {
        passErrorToProxyListener(info, e);
    }

    private void dispatchOutgoingMessage(ProtocolMessage message) {
        Logger.i(LOG_TAG + " Sending Protocol Msg, name:" +
                FunctionID.getFunctionName(message.getFunctionID()) +
                " type:" + message.getRPCType());
        mSyncConnection.sendMessage(message);
    }

    private void handleErrorsFromOutgoingMessageDispatcher(String info, Exception e) {
        passErrorToProxyListener(info, e);
    }

    void dispatchInternalMessage(final InternalProxyMessage message) {
        try {
            if (message.getFunctionName().equals(Names.OnProxyError)) {
                final OnError msg = (OnError) message;
                if (_callbackToUIThread) {
                    // Run in UI thread
                    _mainUIHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            _proxyListener.onError(msg.getInfo(), msg.getThrowable());
                        }
                    });
                } else {
                    Logger.d("Error info:" + msg.getThrowable().toString());
                    _proxyListener.onError(msg.getInfo(), msg.getThrowable());
                }
                /**************Start Legacy Specific Call-backs************/
            } else if (message.getFunctionName().equals(Names.OnProxyOpened)) {
                if (_callbackToUIThread) {
                    // Run in UI thread
                    _mainUIHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            ((IProxyListener) _proxyListener).onProxyOpened();
                        }
                    });
                } else {
                    ((IProxyListener) _proxyListener).onProxyOpened();
                }
            } else if (message.getFunctionName().equals(Names.OnProxyClosed)) {
                final OnProxyClosed msg = (OnProxyClosed) message;
                if (_callbackToUIThread) {
                    // Run in UI thread
                    _mainUIHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            _proxyListener.onProxyClosed(msg.getInfo(), msg.getException());
                        }
                    });
                } else {
                    _proxyListener.onProxyClosed(msg.getInfo(), msg.getException());
                }
                /****************End Legacy Specific Call-backs************/
            } else {
                // Diagnostics
                Logger.i("Unknown RPC Message encountered. Check for an updated version of the SYNC Proxy.");
                Logger.e("Unknown RPC Message encountered. Check for an updated version of the SYNC Proxy.");
            }

            Logger.i("Proxy fired callback: " + message.getFunctionName());
        } catch (final Exception e) {
            // Pass error to application through listener
            Logger.e("Error handing proxy event.", e);
            if (_callbackToUIThread) {
                // Run in UI thread
                _mainUIHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        _proxyListener.onError("Error handing proxy event.", e);
                    }
                });
            } else {
                _proxyListener.onError("Error handing proxy event.", e);
            }
        }
    }

    private void handleErrorsFromInternalMessageDispatcher(String info, Exception e) {
        // This error cannot be passed to the user, as it indicates an error
        // in the communication between the proxy and the application.

        Logger.e("InternalMessageDispatcher failed.", e);

        // Note, this is the only place where the _proxyListener should be referenced asynchronously,
        // with an error on the internalMessageDispatcher, we have no other reliable way of
        // communicating with the application.
        notifyProxyClosed("Proxy callback dispatcher is down. Proxy instance is invalid.", e);
        _proxyListener.onError(
                "Proxy callback dispatcher is down. Proxy instance is invalid.",
                e);
    }

    /**
     * ********** END Functions used by the Message Dispatching Queues ***************
     */

    // Private sendPRCRequest method. All RPCRequests are funneled through this method after
    // error checking.
    // FIXME: return to private?
    void sendRPCRequestPrivate(byte sessionId, RPCRequest request) throws SyncException {

        Logger.d(LOG_TAG + " Send RPC sesId:" + sessionId + " name:" + request.getFunctionName());

        try {
            final IRPCRequestConverter converter =
                    rpcRequestConverterFactory.getConverterForRequest(request);
            List<ProtocolMessage> protocolMessages =
                    converter.getProtocolMessages(request,
                            sessionId, _jsonRPCMarshaller,
                            mSyncConnection.getProtocolVersion());

            if (protocolMessages.size() > 0) {
                queueOutgoingMessage(protocolMessages.get(0));
                protocolMessages.remove(0);

                if (protocolMessages.size() > 0) {
                    protocolMessageHolder.saveMessages(protocolMessages);
                }
            }
        } catch (OutOfMemoryError e) {
            Logger.e("OutOfMemory exception while sending request " + request.getFunctionName());
            throw new SyncException("OutOfMemory exception while sending request " +
                    request.getFunctionName(), e, SyncExceptionCause.INVALID_ARGUMENT);
        }
    }

    // Private sendPRCRequest method. All RPCRequests are funneled through this method after
    // error checking.
    // FIXME: return to private?
    void sendRPCRequestPrivate(String appId, RPCRequest request) throws SyncException {
        byte sessionId = syncSession.getSessionIdByAppId(appId);
        sendRPCRequestPrivate(sessionId, request);
    }

    private void queueOutgoingMessage(ProtocolMessage message) {
        //synchronized (OUTGOING_MESSAGE_QUEUE_THREAD_LOCK) {
            if (_outgoingProxyMessageDispatcher != null) {
                _outgoingProxyMessageDispatcher.queueMessage(message);
            }
        //}
    }

    /**
     * Handles a response that is a part of partial request (i.e., split into
     * multiple protocol messages) if it is.
     *
     * @param response response from the SDL
     * @param hash serialized hashtable of the response
     * @return true if the response has been handled; false when the
     * corresponding request is not partial or in case of an error
     */
    protected boolean handlePartialRPCResponse(final byte sessionId, final RPCResponse response,
                                               Hashtable hash) {
        boolean success = false;
        final Integer responseCorrelationID = response.getCorrelationID();
        final String appId = syncSession.getAppIdBySessionId(sessionId);
        if (protocolMessageHolder.hasMessages(responseCorrelationID)) {
            if (Result.SUCCESS == response.getResultCode()) {
                final ProtocolMessage pm = protocolMessageHolder.peekNextMessage(
                        responseCorrelationID);
                if (pm.getFunctionID() == FunctionID.getFunctionID(response.getFunctionName())) {
                    protocolMessageHolder.popNextMessage(responseCorrelationID);

                    //
                    // Send partial response message to the application
                    //

                    if (response.getFunctionName() != null &&
                            response.getFunctionName().equals(Names.PutFile)) {
                        final PutFileResponse putFile = new PutFileResponse(hash);
                        if (_callbackToUIThread) {
                            // Run in UI thread
                            _mainUIHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    _proxyListener.onPutFileResponse(appId, putFile);
                                }
                            });
                        } else {
                            _proxyListener.onPutFileResponse(appId, putFile);
                        }
                    }

                    queueOutgoingMessage(pm);
                    success = true;
                }
            } else {
                protocolMessageHolder.clearMessages(responseCorrelationID);
            }
        }

        return success;
    }

    /**
     * Handles an internal response, if it is, that is last in sequence. Such a
     * response shouldn't be exposed to the user. For example, a PutFile
     * responded for OnSystemRequest.
     *
     * @param response response from the SDL
     * @return true if the response has been handled; false when the
     * corresponding request is not internal or in case of an error
     */
    protected boolean handleLastInternalResponse(RPCResponse response) {
        final Integer correlationID = response.getCorrelationID();
        final boolean contains = internalRequestCorrelationIDs.contains(correlationID);
        if (contains) {
            internalRequestCorrelationIDs.remove(correlationID);
        }
        return contains;
    }

    protected void handleOnSystemRequest(final String appId, Hashtable hash) {
        final OnSystemRequest msg = new OnSystemRequest(hash);
        if (_callbackToUIThread) {
            // Run in UI thread
            _mainUIHandler.post(new Runnable() {
                @Override
                public void run() {
                    _proxyListener.onOnSystemRequest(appId, msg);
                }
            });
        } else {
            _proxyListener.onOnSystemRequest(appId, msg);
        }

        final FileType fileType = msg.getFileType();
        final RequestType requestType = msg.getRequestType();

        if (requestType == RequestType.HTTP) {
            if (fileType == FileType.BINARY) {
                Logger.d(LOG_TAG + " PolicyTableSnapshot url:" + msg.getUrl());
                processPolicyTableSnapshot(appId, msg.getBulkData(), fileType, requestType);
            } else {
                final Vector<String> urls = msg.getUrl();
                if (urls != null) {
                    Runnable request = new Runnable() {
                        @Override
                        public void run() {
                            onSystemRequestHandler.onFilesDownloadRequest(appId,
                                    SyncProxyBase.this, urls, fileType);
                        }
                    };
                    if (_callbackToUIThread) {
                        _mainUIHandler.post(request);
                    } else {
                        request.run();
                    }
                } else {
                    Logger.w("OnSystemRequest HTTP: no urls set");
                }
            }
        } else if (requestType == RequestType.FILE_RESUME) {
            final Vector<String> urls = msg.getUrl();
            final Integer offset = msg.getOffset();
            final Integer length = msg.getLength();
            final boolean allRequiredParamsSet =
                    (urls != null) && (offset != null) && (length != null);
            if (allRequiredParamsSet) {
                Runnable request = new Runnable() {
                    @Override
                    public void run() {
                        onSystemRequestHandler.onFileResumeRequest(appId,
                                SyncProxyBase.this, urls.get(0), offset, length, fileType);
                    }
                };

                if (_callbackToUIThread) {
                    _mainUIHandler.post(request);
                } else {
                    request.run();
                }
            } else {
                Logger.w("OnSystemRequest FILE_RESUME: a required parameter is missing");
            }
        } else if (requestType == RequestType.PROPRIETARY) {
            if (fileType == FileType.JSON) {
                processPolicyTableSnapshot(appId, msg.getBulkData(), fileType, requestType);
            }
        }
    }

    protected void notifyOnAppInterfaceUnregistered(final OnAppInterfaceUnregistered msg) {
        notifyProxyClosed("OnAppInterfaceUnregistered", null);

        if (_callbackToUIThread) {
            // Run in UI thread
            _mainUIHandler.post(new Runnable() {
                @Override
                public void run() {
                    ((IProxyListener) _proxyListener).onOnAppInterfaceUnregistered(msg);
                }
            });
        } else {
            ((IProxyListener) _proxyListener).onOnAppInterfaceUnregistered(msg);
        }
    }

    protected void onAppUnregisteredReason(final byte sessionId,
                                           final AppInterfaceUnregisteredReason reason) {
        if (reason == AppInterfaceUnregisteredReason.IGNITION_OFF ||
                reason == AppInterfaceUnregisteredReason.MASTER_RESET) {
            cycleProxy(SyncDisconnectedReason.convertAppInterfaceUnregisteredReason(reason));
        }

        final String appId = syncSession.getAppIdBySessionId(sessionId);
        if (getCallbackToUIThread()) {
            // Run in UI thread
            getMainUIHandler().post(new Runnable() {
                @Override
                public void run() {
                    getProxyListener().onAppUnregisteredReason(appId, reason);
                }
            });
        } else {
            getProxyListener().onAppUnregisteredReason(appId, reason);
        }
    }

    protected void onUnregisterAppInterfaceResponse(final byte sessionId, Hashtable hash) {
        final String appId = syncSession.getAppIdBySessionId(sessionId);

        endSession(sessionId);

        // UnregisterAppInterface
        mAppInterfaceRegistered.put(syncSession.getAppIdBySessionId(sessionId), false);
        synchronized (APP_INTERFACE_REGISTERED_LOCK) {
            APP_INTERFACE_REGISTERED_LOCK.notify();
        }
        final UnregisterAppInterfaceResponse msg = new UnregisterAppInterfaceResponse(hash);
        if (_callbackToUIThread) {
            // Run in UI thread
            _mainUIHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (_proxyListener instanceof IProxyListener) {
                        ((IProxyListener) _proxyListener).onUnregisterAppInterfaceResponse(msg);
                    } else if (_proxyListener instanceof IProxyListenerALMTesting) {
                        ((IProxyListenerALMTesting) _proxyListener)
                                .onUnregisterAppInterfaceResponse(appId, msg);
                    }
                }
            });
        } else {
            if (_proxyListener instanceof IProxyListener) {
                ((IProxyListener) _proxyListener).onUnregisterAppInterfaceResponse(msg);
            } else if (_proxyListener instanceof IProxyListenerALMTesting) {
                ((IProxyListenerALMTesting) _proxyListener)
                        .onUnregisterAppInterfaceResponse(appId, msg);
            }
        }
        //notifyProxyClosed("UnregisterAppInterfaceResponse", null);
    }

    /**
     * Invalidates provided Application Id, clear all Services associated and remove it from the list
     *
     * @param appId Application identifier
     */
    public void invalidateAppId(String appId) {
        syncSession.stopSession(appId);
        //syncSession.invalidateAppId(appId);
    }

    /**
     * Takes an {@link com.ford.syncV4.proxy.RPCRequest} and sends it to SYNC. Responses are
     * captured through callback on {@link com.ford.syncV4.proxy.IProxyListener}.
     *
     * @throws SyncException
     */
    public void sendRPCRequest(String appId, RPCRequest request) throws SyncException {
        if (_proxyDisposed) {
            throw new SyncException("This object has been disposed, it is no long capable of " +
                    "executing methods.", SyncExceptionCause.SYNC_PROXY_DISPOSED);
        }

        // Test if request is null
        if (request == null) {
            Logger.w("Application called sendRPCRequest method with a null RPCRequest.");
            throw new IllegalArgumentException("sendRPCRequest cannot be called with a null " +
                    "request.");
        }

        Logger.i("Application called sendRPCRequest method for RPCRequest: ." +
                request.getFunctionName());

        checkSyncConnection();

        // Test for illegal correlation ID
        if (isCorrelationIDProtected(request.getCorrelationID())) {

            Logger.w("Application attempted to use the reserved correlation ID, " +
                    request.getCorrelationID());
            throw new SyncException("Invalid correlation ID. The correlation ID, " +
                    request.getCorrelationID()
                    + " , is a reserved correlation ID.", SyncExceptionCause.RESERVED_CORRELATION_ID);
        }

        // Throw exception if RPCRequest is sent when SYNC is unavailable
        Boolean isAppInterfaceRegistered = mAppInterfaceRegistered.get(appId);
        if (isAppInterfaceRegistered == null) {
            isAppInterfaceRegistered = false;
        }
        if (!isAppInterfaceRegistered &&
                !request.getFunctionName().equals(Names.RegisterAppInterface)) {
            if (!allowExtraTesting()) {
                Logger.w("Application attempted to send an RPCRequest (non-registerAppInterface), " +
                        "before the interface was registerd.");
                throw new SyncException("SYNC is currently unavailable. RPC Requests cannot be " +
                        "sent.", SyncExceptionCause.SYNC_UNAVAILALBE);
            }
        }

        if (_advancedLifecycleManagementEnabled) {
            if (request.getFunctionName().equals(Names.RegisterAppInterface)
                    || request.getFunctionName().equals(Names.UnregisterAppInterface)) {
                if (!allowExtraTesting()) {
                    Logger.w("Application attempted to send a RegisterAppInterface or " +
                            "UnregisterAppInterface while using ALM.");
                    throw new SyncException("The RPCRequest, " + request.getFunctionName() +
                            ", is unnallowed using the Advanced Lifecycle Management Model.",
                            SyncExceptionCause.INCORRECT_LIFECYCLE_MODEL);
                }
            }
        }

        sendRPCRequestPrivate(appId, request);
    }

    private void checkSyncConnection() throws SyncException {
        // Test if SyncConnection is null
        synchronized (CONNECTION_REFERENCE_LOCK) {
            if (mSyncConnection == null || !mSyncConnection.getIsConnected()) {
                Logger.i("Application attempted to send and RPCRequest without a connected transport.");
                throw new SyncException("There is no valid connection to SYNC. sendRPCRequest " +
                        "cannot be called until SYNC has been connected.",
                        SyncExceptionCause.SYNC_UNAVAILALBE);
            }
        }
    }

    /**
     * Returns whether the class allows some extra testing features. For this,
     * the _proxyListener must be an instance of the IProxyListenerALMTesting
     * class.
     *
     * @return true if the extra testing features are enabled
     */
    private boolean allowExtraTesting() {
        return _proxyListener instanceof IProxyListenerALMTesting;
    }

    protected void notifyProxyClosed(final String info, final Exception e) {
        Logger.i("NotifyProxyClose");

        OnProxyClosed message = new OnProxyClosed(info, e);
        queueInternalMessage(message);
    }

    private void passErrorToProxyListener(final String info, final Throwable e) {
        OnError message = new OnError(info, e);
        queueInternalMessage(message);
    }

    private void startProtocolSession(final byte sessionId) {
        final String associatedAppId = syncSession.updateSessionId(sessionId);
        Logger.d("Start Protocol session appId:" + associatedAppId + " sesId:" + sessionId +
                " exists:" + syncSession.hasService(associatedAppId, ServiceType.RPC));
        if (!syncSession.hasService(associatedAppId, ServiceType.RPC)) {
            Service service = new Service();
            service.setServiceType(ServiceType.RPC);
            service.setAppId(associatedAppId);
            syncSession.addService(service);
        }

        if (_callbackToUIThread) {
            // Run in UI thread
            _mainUIHandler.post(new Runnable() {
                @Override
                public void run() {
                    _proxyListener.onSessionStarted(associatedAppId);
                }
            });
        } else {
            _proxyListener.onSessionStarted(associatedAppId);
        }

        restartRPCProtocolSession(sessionId);
    }

    private void restartRPCProtocolSession(byte sessionId) {
        // Set Proxy Lifecycle Available
        if (_advancedLifecycleManagementEnabled) {

            // For the Test Cases
            if (mTestConfig != null) {
                Logger.d("RestartRPCProtocolSession DoCallRegisterAppInterface: " +
                        mTestConfig.isDoCallRegisterAppInterface());
                if (!mTestConfig.isDoCallRegisterAppInterface()) {

                    Logger.d("RestartRPCProtocolSession TestConfigCallback: " + mTestConfigCallback);
                    if (mTestConfigCallback != null) {
                        mTestConfigCallback.onRPCServiceComplete();
                    }

                    // Revert back a value which has been set for concrete Test Case
                    mTestConfig.setDoCallRegisterAppInterface(true);

                    return;
                }
            }

            DeviceInfoManager.dumpDeviceInfo(getDeviceInfo());
            String appId = syncSession.getAppIdBySessionId(sessionId);

            Logger.d("RestartRPCProtocolSession sesId:" + sessionId + " appId:" + appId);

            RegisterAppInterface registerAppInterface = raiTable.get(appId);
            if (registerAppInterface == null) {
                return;
            }

            try {
                registerAppInterfacePrivate(
                        registerAppInterface.getSyncMsgVersion(),
                        registerAppInterface.getAppName(),
                        registerAppInterface.getTtsName(),
                        registerAppInterface.getNgnMediaScreenAppName(),
                        registerAppInterface.getVrSynonyms(),
                        registerAppInterface.getIsMediaApplication(),
                        registerAppInterface.getLanguageDesired(),
                        registerAppInterface.getHmiDisplayLanguageDesired(),
                        registerAppInterface.getAppType(),
                        registerAppInterface.getAppID(),
                        getRegisterAppInterfaceCorrelationId(),
                        getHashId(appId), getDeviceInfo());

            } catch (Exception e) {
                notifyProxyClosed("Failed to register application interface with SYNC. " +
                        "Check parameter values given to SyncProxy constructor.", e);
            }
        } else {
            InternalProxyMessage message = new InternalProxyMessage(Names.OnProxyOpened);
            queueInternalMessage(message);
        }
    }

    protected void handleEndService(final ServiceType serviceType, final byte sessionId) {
        final String appId = syncSession.getAppIdBySessionId(sessionId);

        endSession(sessionId);

        if (_callbackToUIThread) {
            // Run in UI thread
            _mainUIHandler.post(new Runnable() {
                @Override
                public void run() {
                    _proxyListener.onProtocolServiceEnded(serviceType, appId);
                }
            });
        } else {
            _proxyListener.onProtocolServiceEnded(serviceType, appId);
        }
    }

    protected void handleEndServiceAck(final ServiceType serviceType, final byte sessionId) {
        final String appId = syncSession.getAppIdBySessionId(sessionId);
        if (_callbackToUIThread) {
            // Run in UI thread
            _mainUIHandler.post(new Runnable() {
                @Override
                public void run() {
                    _proxyListener.onProtocolServiceEndedAck(serviceType, appId);
                }
            });
        } else {
            _proxyListener.onProtocolServiceEndedAck(serviceType, appId);
        }
    }

    protected void handleMobileNavAck(final byte sessionId, int frameNumberReceived) {
        Logger.i("Mobile Nav Ack received = " + frameNumberReceived);
        final int fNumber = frameNumberReceived;
        final String appId = syncSession.getAppIdBySessionId(sessionId);
        if (_callbackToUIThread) {
            // Run in UI thread
            _mainUIHandler.post(new Runnable() {
                @Override
                public void run() {
                    _proxyListener.onMobileNavAckReceived(appId, fNumber);
                }
            });
        } else {
            _proxyListener.onMobileNavAckReceived(appId, fNumber);
        }
    }

    protected void handleStartServiceNack(final byte sessionId, final ServiceType serviceType) {
        Logger.i("Service Nack received for " + serviceType);
        final String appId = syncSession.getAppIdBySessionId(sessionId);
        if (_callbackToUIThread) {
            // Run in UI thread
            _mainUIHandler.post(new Runnable() {
                @Override
                public void run() {
                    _proxyListener.onStartServiceNackReceived(appId, serviceType);
                }
            });
        } else {
            _proxyListener.onStartServiceNackReceived(appId, serviceType);
        }
    }

    protected void onProtocolServiceStarted_MobileNavi(final byte sessionId) {
        Logger.i("Mobile Navi service started");
        createService(sessionId, ServiceType.Mobile_Nav);
        final String appId = syncSession.getAppIdBySessionId(sessionId);
        if (_callbackToUIThread) {
            // Run in UI thread
            _mainUIHandler.post(new Runnable() {
                @Override
                public void run() {
                    _proxyListener.onMobileNaviStart(appId);
                }
            });
        } else {
            _proxyListener.onMobileNaviStart(appId);
        }
    }

    protected void onProtocolServiceStarted_Audio(final byte sessionId) {
        Logger.i("Mobile Audio service started, sesId:" + sessionId);
        createService(sessionId, ServiceType.Audio_Service);
        final String appId = syncSession.getAppIdBySessionId(sessionId);
        if (_callbackToUIThread) {
            // Run in UI thread
            _mainUIHandler.post(new Runnable() {
                @Override
                public void run() {
                    _proxyListener.onAudioServiceStart(appId);
                }
            });
        } else {
            _proxyListener.onAudioServiceStart(appId);
        }
    }

    private void createService(byte sessionId, ServiceType serviceType) {
        if (!syncSession.hasSessionId(sessionId)) {
            throw new IllegalArgumentException("can't create service with sesId:" + sessionId);
        }
        String appId = syncSession.getAppIdBySessionId(sessionId);
        Service service = syncSession.createService(appId, serviceType);
        syncSession.addService(service);
    }

    public void startAudioService(String appId) {
        if (getSyncConnection() == null) {
            // TODO : Msg here
            return;
        }
        byte sessionId = syncSession.getSessionIdByAppId(appId);
        getSyncConnection().startAudioService(sessionId);
    }

    public void startMobileNavService(String appId) {
        if (getSyncConnection() == null) {
            // TODO : Msg here
            return;
        }
        byte sessionId = syncSession.getSessionIdByAppId(appId);
        getSyncConnection().startMobileNavService(sessionId);
    }

    public void stopMobileNaviService(String appId) {
        final byte sessionId = syncSession.getSessionIdByAppId(appId);
        if (removeServiceFromSession(appId, ServiceType.Mobile_Nav)) {
            Logger.i("Mobile Navi Service is going to stop, sesId:" + sessionId + " appId:" + appId);
            if (getSyncConnection() != null) {
                getSyncConnection().closeMobileNaviService(sessionId);
            }
        } else {
            Logger.w("Mobile Navi service not found at sesId:" + sessionId + " appId:" + appId);
        }
    }

    public void stopAudioService(String appId) {
        final byte sessionId = syncSession.getSessionIdByAppId(appId);
        if (removeServiceFromSession(appId, ServiceType.Audio_Service)) {
            Logger.i("Mobile Audio service is going to stop, sesId:" + sessionId + " appId:" + appId);
            if (getSyncConnection() != null) {
                getSyncConnection().closeAudioService(sessionId);
            }
        } else {
            Logger.w("Audio service not found at sesId:" + sessionId + " appId:" + appId);
        }
    }

    private boolean removeServiceFromSession(String appId, ServiceType serviceType) {
        return syncSession.removeService(appId, serviceType);
    }

    /**
     * Will be removed soon, do not use it any more
     * @return
     */
    @Deprecated
    public List<Service> getServicePool() {
        return syncSession.getServicePool();
    }

    // Queue internal callback message
    private void queueInternalMessage(InternalProxyMessage message) {
        synchronized (INTERNAL_MESSAGE_QUEUE_THREAD_LOCK) {
            if (_internalProxyMessageDispatcher != null) {
                _internalProxyMessageDispatcher.queueMessage(message);
            }
        }
    }

    // Queue incoming ProtocolMessage
    private void queueIncomingMessage(ProtocolMessage message) {
        synchronized (INCOMING_MESSAGE_QUEUE_THREAD_LOCK) {
            if (_incomingProxyMessageDispatcher != null) {
                _incomingProxyMessageDispatcher.queueMessage(message);
            }
        }
    }

    public OutputStream startH264(String appId) {
        OutputStream stream = null;
        if (mSyncConnection != null) {
            byte sessionId = getSessionIdByAppId(appId);
            stream = mSyncConnection.startH264(sessionId);
        }
        return stream;
    }

    public void stopH264() {
        if (mSyncConnection != null) {
            mSyncConnection.stopH264();
        }
    }

    public OutputStream startAudioDataTransfer(String appId) {
        OutputStream stream = null;
        if (mSyncConnection != null) {
            byte sessionId = syncSession.getSessionIdByAppId(appId);
            stream = mSyncConnection.startAudioDataTransfer(sessionId);
        }
        return stream;
    }

    public void stopAudioDataTransfer() {
        if (mSyncConnection != null) {
            mSyncConnection.stopAudioDataTransfer();
        }
    }

    /**
     * Sends an AddCommand RPCRequest to SYNC. Responses are captured through callback on IProxyListener.
     *
     * @param commandID command Id
     * @param menuText menu text
     * @param parentID parent Id
     * @param position position
     * @param vrCommands VR Commands vector
     * @param correlationID correlation Id
     * @throws SyncException
     */
    public void addCommand(String appId, Integer commandID, String menuText, Integer parentID,
                           Integer position, Vector<String> vrCommands, Integer correlationID)
            throws SyncException {

        AddCommand addCommand = RPCRequestFactory.buildAddCommand(commandID, menuText, parentID,
                position, vrCommands, correlationID);

        sendRPCRequest(appId, addCommand);
    }

    /**
     * Sends an AddCommand RPCRequest to SYNC. Responses are captured through callback on IProxyListener.
     *
     * @param commandID command Id
     * @param menuText
     * @param position
     * @param vrCommands
     * @param correlationID
     * @throws SyncException
     */
    public void addCommand(String appId, Integer commandID, String menuText, Integer position,
                           Vector<String> vrCommands, Integer correlationID)
            throws SyncException {

        addCommand(appId, commandID, menuText, null, position, vrCommands, correlationID);
    }

    /**
     * Sends an AddCommand RPCRequest to SYNC. Responses are captured through callback on IProxyListener.
     *
     * @param commandID command Id
     * @param menuText
     * @param position
     * @param correlationID
     * @throws SyncException
     */
    public void addCommand(String appId, Integer commandID, String menuText, Integer position,
                           Integer correlationID)
            throws SyncException {

        addCommand(appId, commandID, menuText, null, position, null, correlationID);
    }

    /**
     * Sends an AddCommand RPCRequest to SYNC. Responses are captured through callback on IProxyListener.
     *
     * @param commandID command Id
     * @param menuText
     * @param correlationID
     * @throws SyncException
     */
    public void addCommand(String appId, Integer commandID, String menuText, Integer correlationID)
            throws SyncException {

        addCommand(appId, commandID, menuText, null, null, null, correlationID);
    }

    /**
     * Sends an AddCommand RPCRequest to SYNC. Responses are captured through callback on IProxyListener.
     *
     * @param commandID command Id
     * @param menuText menu text
     * @param vrCommands VR Commands vector
     * @param correlationID correlation Id
     * @throws SyncException
     */
    public void addCommand(String appId, Integer commandID, String menuText, Vector<String> vrCommands,
                           Integer correlationID)
            throws SyncException {
        addCommand(appId, commandID, menuText, null, null, vrCommands, correlationID);
    }

    /**
     * Sends an AddCommand RPCRequest to SYNC. Responses are captured through callback on IProxyListener.
     *
     * @param commandID command Id
     * @param vrCommands
     * @param correlationID
     * @throws SyncException
     */
    public void addCommand(String appId, Integer commandID, Vector<String> vrCommands,
                           Integer correlationID)
            throws SyncException {

        addCommand(appId, commandID, null, null, null, vrCommands, correlationID);
    }

    /**
     * Sends an AddSubMenu RPCRequest to SYNC. Responses are captured through callback on
     * IProxyListener.
     *
     * @param menuID
     * @param menuName
     * @param position
     * @param correlationID
     * @throws SyncException
     */
    public void addSubMenu(String appId, Integer menuID, String menuName, Integer position,
                           Integer correlationID)
            throws SyncException {

        AddSubMenu msg = RPCRequestFactory.buildAddSubMenu(menuID, menuName,
                position, correlationID);

        sendRPCRequest(appId, msg);
    }

    /**
     * Sends an AddSubMenu RPCRequest to SYNC. Responses are captured through callback on
     * IProxyListener.
     *
     * @param menuID
     * @param menuName
     * @param correlationID
     * @throws SyncException
     */
    public void addSubMenu(String appId, Integer menuID, String menuName, Integer correlationID)
            throws SyncException {

        addSubMenu(appId, menuID, menuName, null, correlationID);
    }

    /**
     * Send a ListFiles RPCRequest to SYNC. Responses are captured through callback on IProxyListener.
     *
     * @param correlationID correlation Id
     * @throws SyncException
     */
    public void listFiles(String appId, Integer correlationID) throws SyncException {
        ListFiles listFiles = new ListFiles();
        listFiles.setCorrelationID(correlationID);
        sendRPCRequest(appId, listFiles);
    }

    /**
     * Send a SetAppIcon RPCRequest to SYNC. Responses are captured through callback on IProxyListener.
     *
     * @param fileName a name of the file
     * @param correlationID correlation Id
     * @throws SyncException
     */
    public void setAppIcon(String appId, String fileName, Integer correlationID) throws SyncException {
        SetAppIcon setAppIcon = new SetAppIcon();
        setAppIcon.setSyncFileName(fileName);
        setAppIcon.setCorrelationID(correlationID);
        sendRPCRequest(appId, setAppIcon);
    }

    /**
     * Send PutFile RPCRequest to SYNC. Responses are captured through callback on IProxyListener.
     *
     * @param putFile PutFile object to be send
     */
    public void putFile(String appId, PutFile putFile) throws SyncException {
        if (putFile != null) {
            sendRPCRequest(appId, putFile);
        } else {
            // TODO : Process null object here
        }
    }

    /**
     * Sends an EncodedData RPCRequest to SYNC. Responses are captured through callback on IProxyListener.
     *
     * @param data
     * @param correlationID
     * @throws SyncException
     */
    public void encodedSyncPData(String appId, Vector<String> data, Integer correlationID)
            throws SyncException {

        Logger.i("encodedSyncPData() giving to sync");
        EncodedSyncPData msg = RPCRequestFactory.buildEncodedSyncPData(data, correlationID);
        sendRPCRequest(appId, msg);
    }

    /**
     * Sends a Data RPCRequest to SYNC. Responses are captured through callback on IProxyListener.
     *
     * @param data
     * @param correlationID
     * @throws SyncException
     */
    public void syncPData(String appId, byte[] data, Integer correlationID)
            throws SyncException {

        Logger.i("syncPData() giving to sync");
        SyncPData msg = RPCRequestFactory.buildSyncPData(data, correlationID);
        sendRPCRequest(appId, msg);
    }

    /**
     * Sends an Alert RPCRequest to SYNC. Responses are captured through callback on IProxyListener.
     *
     * @param ttsText
     * @param alertText1
     * @param alertText2
     * @param playTone
     * @param duration
     * @param correlationID
     * @throws SyncException
     */
    public void alert(String appId, String ttsText, String alertText1,
                      String alertText2, Boolean playTone, Integer duration,
                      Integer correlationID) throws SyncException {

        Alert msg = RPCRequestFactory.buildAlert(ttsText, alertText1, alertText2,
                playTone, duration, correlationID);

        sendRPCRequest(appId, msg);
    }

    /**
     * Sends an Alert RPCRequest to SYNC. Responses are captured through callback on IProxyListener.
     *
     * @param ttsChunks
     * @param alertText1
     * @param alertText2
     * @param playTone
     * @param duration
     * @param correlationID
     * @throws SyncException
     */
    public void alert(String appId, Vector<TTSChunk> ttsChunks,
                      String alertText1, String alertText2, Boolean playTone,
                      Integer duration, Integer correlationID) throws SyncException {

        Alert msg = RPCRequestFactory.buildAlert(ttsChunks, alertText1, alertText2, playTone,
                duration, correlationID);

        sendRPCRequest(appId, msg);
    }

    /**
     * Sends an Alert RPCRequest to SYNC. Responses are captured through callback on IProxyListener.
     *
     * @param ttsText
     * @param playTone
     * @param correlationID
     * @throws SyncException
     */
    public void alert(String appId, String ttsText, Boolean playTone,
                      Integer correlationID) throws SyncException {

        alert(appId, ttsText, null, null, playTone, null, correlationID);
    }

    /**
     * Sends an Alert RPCRequest to SYNC. Responses are captured through callback on IProxyListener.
     *
     * @param chunks
     * @param playTone
     * @param correlationID
     * @throws SyncException
     */
    public void alert(String appId, Vector<TTSChunk> chunks, Boolean playTone,
                      Integer correlationID) throws SyncException {

        alert(appId, chunks, null, null, playTone, null, correlationID);
    }

    /**
     * Sends an Alert RPCRequest to SYNC. Responses are captured through callback on IProxyListener.
     *
     * @param alertText1
     * @param alertText2
     * @param playTone
     * @param duration
     * @param correlationID
     * @throws SyncException
     */
    public void alert(String appId, String alertText1, String alertText2,
                      Boolean playTone, Integer duration, Integer correlationID)
            throws SyncException {

        alert(appId, (Vector<TTSChunk>) null, alertText1, alertText2, playTone, duration, correlationID);
    }

    /**
     * Sends a CreateInteractionChoiceSet RPCRequest to SYNC. Responses are captured through callback on IProxyListener.
     *
     * @param choiceSet Set of {@link com.ford.syncV4.proxy.rpc.Choice} objects
     * @param interactionChoiceSetID Id of the interaction Choice set
     * @param correlationID correlation Id
     * @throws SyncException
     */
    public void createInteractionChoiceSet(String appId, Vector<Choice> choiceSet, Integer interactionChoiceSetID,
                                           Integer correlationID) throws SyncException {

        CreateInteractionChoiceSet createInteractionChoiceSet =
                RPCRequestFactory.buildCreateInteractionChoiceSet(choiceSet,
                        interactionChoiceSetID, correlationID);

        sendRPCRequest(appId, createInteractionChoiceSet);
    }

    /**
     * Sends a DeleteCommand RPCRequest to SYNC. Responses are captured through callback on IProxyListener.
     *
     * @param commandID
     * @param correlationID
     * @throws SyncException
     */
    public void deleteCommand(String appId, Integer commandID,
                              Integer correlationID) throws SyncException {

        DeleteCommand msg = RPCRequestFactory.buildDeleteCommand(commandID, correlationID);

        sendRPCRequest(appId, msg);
    }

    /**
     * Sends a DeleteInteractionChoiceSet RPCRequest to SYNC. Responses are captured through callback on IProxyListener.
     *
     * @param interactionChoiceSetID
     * @param correlationID
     * @throws SyncException
     */
    public void deleteInteractionChoiceSet(String appId,
                                           Integer interactionChoiceSetID, Integer correlationID)
            throws SyncException {

        DeleteInteractionChoiceSet msg = RPCRequestFactory.buildDeleteInteractionChoiceSet(
                interactionChoiceSetID, correlationID);

        sendRPCRequest(appId, msg);
    }

    /**
     * Sends a DeleteSubMenu RPCRequest to SYNC. Responses are captured through callback on IProxyListener.
     *
     * @param menuID
     * @param correlationID
     * @throws SyncException
     */
    public void deleteSubMenu(String appId, Integer menuID,
                              Integer correlationID) throws SyncException {

        DeleteSubMenu msg = RPCRequestFactory.buildDeleteSubMenu(menuID, correlationID);

        sendRPCRequest(appId, msg);
    }

    /**
     * Sends a PerformInteraction RPCRequest to SYNC. Responses are captured through callback on IProxyListener.
     *
     * @param initPrompt
     * @param displayText
     * @param interactionChoiceSetID
     * @param correlationID
     * @throws SyncException
     */
    public void performInteraction(String appId, String initPrompt,
                                   String displayText, Integer interactionChoiceSetID,
                                   Integer correlationID) throws SyncException {

        PerformInteraction msg = RPCRequestFactory.buildPerformInteraction(initPrompt,
                displayText, interactionChoiceSetID, correlationID);

        sendRPCRequest(appId, msg);
    }

    /**
     * Sends a PerformInteraction RPCRequest to SYNC. Responses are captured through callback on IProxyListener.
     *
     * @param initPrompt
     * @param displayText
     * @param interactionChoiceSetID
     * @param correlationID
     * @throws SyncException
     */
    public void performInteraction(String appId, String initPrompt,
                                   String displayText, Integer interactionChoiceSetID,
                                   String helpPrompt, String timeoutPrompt,
                                   InteractionMode interactionMode, Integer timeout,
                                   Integer correlationID) throws SyncException {

        PerformInteraction msg = RPCRequestFactory.buildPerformInteraction(
                initPrompt, displayText, interactionChoiceSetID,
                helpPrompt, timeoutPrompt, interactionMode,
                timeout, correlationID);

        sendRPCRequest(appId, msg);
    }

    /**
     * Sends a PerformInteraction RPCRequest to SYNC. Responses are captured through callback on IProxyListener.
     *
     * @param initPrompt
     * @param displayText
     * @param interactionChoiceSetIDList
     * @param helpPrompt
     * @param timeoutPrompt
     * @param interactionMode
     * @param timeout
     * @param correlationID
     * @throws SyncException
     */
    public void performInteraction(String appId, String initPrompt,
                                   String displayText, Vector<Integer> interactionChoiceSetIDList,
                                   String helpPrompt, String timeoutPrompt,
                                   InteractionMode interactionMode, Integer timeout,
                                   Integer correlationID) throws SyncException {

        PerformInteraction msg = RPCRequestFactory.buildPerformInteraction(initPrompt,
                displayText, interactionChoiceSetIDList,
                helpPrompt, timeoutPrompt, interactionMode, timeout,
                correlationID);

        sendRPCRequest(appId, msg);
    }

    /**
     * Sends a PerformInteraction RPCRequest to SYNC. Responses are captured through callback on IProxyListener.
     *
     * @param initChunks
     * @param displayText
     * @param interactionChoiceSetIDList
     * @param helpChunks
     * @param timeoutChunks
     * @param interactionMode
     * @param timeout
     * @param correlationID
     * @throws SyncException
     */
    public void performInteraction(String appId,
                                   Vector<TTSChunk> initChunks, String displayText,
            Vector<Integer> interactionChoiceSetIDList,
            Vector<TTSChunk> helpChunks, Vector<TTSChunk> timeoutChunks,
            InteractionMode interactionMode, Integer timeout,
            Integer correlationID) throws SyncException {

        PerformInteraction msg = RPCRequestFactory.buildPerformInteraction(
                initChunks, displayText, interactionChoiceSetIDList,
                helpChunks, timeoutChunks, interactionMode, timeout,
                correlationID);

        sendRPCRequest(appId, msg);
    }

    // Protected registerAppInterface used to ensure only non-ALM applications call
    // ReqisterAppInterface
    protected void registerAppInterfacePrivate(
            SyncMsgVersion syncMsgVersion, Object appName, Vector<TTSChunk> ttsName,
            Object ngnMediaScreenAppName, Vector<String> vrSynonyms, Object isMediaApp,
            Language languageDesired, Language hmiDisplayLanguageDesired, Vector<AppHMIType> appHMIType,
            final Object appId, Integer correlationID, String hashId,
            DeviceInfo deviceInfo) throws SyncException {

        final RegisterAppInterface registerAppInterface = RPCRequestFactory.buildRegisterAppInterface(
                syncMsgVersion, appName, ttsName, ngnMediaScreenAppName, vrSynonyms, isMediaApp,
                languageDesired, hmiDisplayLanguageDesired, appHMIType, appId, correlationID, hashId,
                deviceInfo);

        appIds.add((String) appId);
        raiTable.put((String) appId, registerAppInterface);

        sendRPCRequestPrivate((String) appId, registerAppInterface);

        if (_callbackToUIThread) {
            // Run in UI thread
            _mainUIHandler.post(new Runnable() {
                @Override
                public void run() {
                    _proxyListener.onRegisterAppRequest((String) appId, registerAppInterface);
                }
            });
        } else {
            _proxyListener.onRegisterAppRequest((String) appId, registerAppInterface);
        }
    }

    /**
     * Sends a SetGlobalProperties RPCRequest to SYNC. Responses are captured through callback on IProxyListener.
     *
     * @param helpPrompt
     * @param timeoutPrompt
     * @param correlationID
     * @throws SyncException
     */
    public void setGlobalProperties(String appId,
                                    String helpPrompt, String timeoutPrompt, Integer correlationID)
            throws SyncException {

        SetGlobalProperties req = RPCRequestFactory.buildSetGlobalProperties(helpPrompt,
                timeoutPrompt, correlationID);

        sendRPCRequest(appId, req);
    }

    /**
     * Sends a SetGlobalProperties RPCRequest to SYNC. Responses are captured through callback on IProxyListener.
     *
     * @param helpChunks
     * @param timeoutChunks
     * @param correlationID
     * @throws SyncException
     */
    public void setGlobalProperties(String appId,
                                    Vector<TTSChunk> helpChunks, Vector<TTSChunk> timeoutChunks,
            Integer correlationID) throws SyncException {

        SetGlobalProperties req = RPCRequestFactory.buildSetGlobalProperties(
                helpChunks, timeoutChunks, correlationID);

        sendRPCRequest(appId, req);
    }

    public void resetGlobalProperties(String appId, Vector<GlobalProperty> properties,
                                      Integer correlationID) throws SyncException {

        ResetGlobalProperties req = new ResetGlobalProperties();

        req.setCorrelationID(correlationID);
        req.setProperties(properties);

        sendRPCRequest(appId, req);
    }

    /**
     * Sends a SetMediaClockTimer RPCRequest to SYNC. Responses are captured through callback on IProxyListener.
     *
     * @param hours
     * @param minutes
     * @param seconds
     * @param updateMode
     * @param correlationID
     * @throws SyncException
     */
    public void setMediaClockTimer(String appId, Integer hours,
                                   Integer minutes, Integer seconds, UpdateMode updateMode,
                                   Integer correlationID) throws SyncException {

        SetMediaClockTimer msg = RPCRequestFactory.buildSetMediaClockTimer(hours,
                minutes, seconds, updateMode, correlationID);

        sendRPCRequest(appId, msg);
    }

    /**
     * Pauses the media clock. Responses are captured through callback on IProxyListener.
     *
     * @param correlationID
     * @throws SyncException
     */
    public void pauseMediaClockTimer(String appId, Integer correlationID)
            throws SyncException {

        SetMediaClockTimer msg = RPCRequestFactory.buildSetMediaClockTimer(0,
                0, 0, UpdateMode.PAUSE, correlationID);

        sendRPCRequest(appId, msg);
    }

    /**
     * Resumes the media clock. Responses are captured through callback on IProxyListener.
     *
     * @param correlationID
     * @throws SyncException
     */
    public void resumeMediaClockTimer(String appId, Integer correlationID)
            throws SyncException {

        SetMediaClockTimer msg = RPCRequestFactory.buildSetMediaClockTimer(0,
                0, 0, UpdateMode.RESUME, correlationID);

        sendRPCRequest(appId, msg);
    }

    /**
     * Clears the media clock. Responses are captured through callback on IProxyListener.
     *
     * @param correlationID
     * @throws SyncException
     */
    public void clearMediaClockTimer(String appId, Integer correlationID)
            throws SyncException {

        Show msg = RPCRequestFactory.buildShow(null, null, null, "     ", null, null, correlationID);

        sendRPCRequest(appId, msg);
    }

    /**
     * Sends a Show RPCRequest to SYNC. Responses are captured through callback on IProxyListener.
     *
     * @param mainText1
     * @param mainText2
     * @param statusBar
     * @param mediaClock
     * @param mediaTrack
     * @param alignment
     * @param correlationID
     * @throws SyncException
     */
    public void show(String appId, String mainText1, String mainText2,
                     String statusBar, String mediaClock, String mediaTrack,
                     TextAlignment alignment, Integer correlationID)
            throws SyncException {

        Show msg = RPCRequestFactory.buildShow(mainText1, mainText2,
                statusBar, mediaClock, mediaTrack,
                alignment, correlationID);

        sendRPCRequest(appId, msg);
    }

    /**
     * Sends a Show RPCRequest to SYNC. Responses are captured through callback on IProxyListener.
     *
     * @param mainText1
     * @param mainText2
     * @param alignment
     * @param correlationID
     * @throws SyncException
     */
    public void show(String appId, String mainText1, String mainText2,
                     TextAlignment alignment, Integer correlationID)
            throws SyncException {

        show(appId, mainText1, mainText2, null, null, null, alignment, correlationID);
    }

    /**
     * Sends a Speak RPCRequest to SYNC. Responses are captured through callback on IProxyListener.
     *
     * @param ttsText
     * @param correlationID
     * @throws SyncException
     */
    public void speak(String appId, String ttsText, Integer correlationID)
            throws SyncException {

        Speak msg = RPCRequestFactory.buildSpeak(TTSChunkFactory.createSimpleTTSChunks(ttsText),
                correlationID);

        sendRPCRequest(appId, msg);
    }

    /**
     * Sends a Speak RPCRequest to SYNC. Responses are captured through callback on IProxyListener.
     *
     * @param ttsChunks
     * @param correlationID
     * @throws SyncException
     */
    public void speak(String appId, Vector<TTSChunk> ttsChunks,
                      Integer correlationID) throws SyncException {

        Speak msg = RPCRequestFactory.buildSpeak(ttsChunks, correlationID);

        sendRPCRequest(appId, msg);
    }

    /**
     * Sends a SubscribeButton RPCRequest to SYNC. Responses are captured through callback on IProxyListener.
     *
     * @param buttonName
     * @param correlationID
     * @throws SyncException
     */
    public void subscribeButton(String appId, ButtonName buttonName, Integer correlationID) throws SyncException {
        SubscribeButton msg = RPCRequestFactory.buildSubscribeButton(buttonName, correlationID);
        sendRPCRequest(appId, msg);
    }

    /**
     * Sends an UnsubscribeButton RPCRequest to SYNC. Responses are captured through callback on
     * IProxyListener.
     *
     * @param buttonName
     * @param correlationID
     * @throws SyncException
     */
    public void unsubscribeButton(String appId, ButtonName buttonName, Integer correlationID)
            throws SyncException {
        UnsubscribeButton msg = RPCRequestFactory.buildUnsubscribeButton(buttonName, correlationID);
        sendRPCRequest(appId, msg);
    }

    /**
     * Creates a choice to be added to a choiceset. Choice has both a voice and a visual menu component.
     *
     * @param choiceID         -Unique ID used to identify this choice (returned in callback).
     * @param choiceMenuName   -Text name displayed for this choice.
     * @param choiceVrCommands -Vector of vrCommands used to select this choice by voice. Must contain
     *                         at least one non-empty element.
     * @return Choice created.
     * @throws SyncException
     */
    public Choice createChoiceSetChoice(Integer choiceID, String choiceMenuName,
                                        Vector<String> choiceVrCommands) {
        Choice returnChoice = new Choice();

        returnChoice.setChoiceID(choiceID);
        returnChoice.setMenuName(choiceMenuName);
        returnChoice.setVrCommands(choiceVrCommands);

        return returnChoice;
    }

    /**
     * Gets type of transport currently used by this SyncProxy.
     *
     * @return One of TransportType enumeration values.
     * @see TransportType
     */
    public TransportType getCurrentTransportType() throws IllegalStateException {
        if (mSyncConnection == null) {
            throw new IllegalStateException("Incorrect state of SyncProxyBase: Calling for getCurrentTransportType() while connection is not initialized");
        }

        return mSyncConnection.getCurrentTransportType();
    }

    /**
     * ***************** END Public Helper Methods ************************
     */

    public IJsonRPCMarshaller getJsonRPCMarshaller() {
        return this._jsonRPCMarshaller;
    }

    /**
     * @param jsonRPCMarshaller the jsonRPCMarshaller to set
     */
    public void setJsonRPCMarshaller(IJsonRPCMarshaller jsonRPCMarshaller) throws IllegalArgumentException {
        if (jsonRPCMarshaller == null) {
            throw new IllegalArgumentException("jsonRPCMarshaller must not be null");
        }

        this._jsonRPCMarshaller = jsonRPCMarshaller;
    }

    protected TimerTask getCurrentReconnectTimerTask() {
        TimerTask task;
        synchronized (RECONNECT_TIMER_TASK_LOCK) {
            task = _currentReconnectTimerTask;
        }
        return task;
    }

    private void setCurrentReconnectTimerTask(TimerTask currentReconnectTimerTask) {
        synchronized (RECONNECT_TIMER_TASK_LOCK) {
            if (currentReconnectTimerTask == null) {
                _currentReconnectTimerTask.cancel();
            }
            _currentReconnectTimerTask = currentReconnectTimerTask;
        }
    }

    private boolean clearCurrentReconnectTimerTask() {
        TimerTask task = getCurrentReconnectTimerTask();
        if (task != null) {
            Logger.d("Clearing reconnect timer task");
            boolean success = task.cancel();
            setCurrentReconnectTimerTask(null);
            if (!success) {
                Logger.i("Can't cancel scheduled reconnect task");
            }
            return success;
        }

        return true;
    }

    private Timer getReconnectTimer() {
        if (_reconnectTimer == null) {
            Logger.d("Reconnect timer is null, creating a new one");
            _reconnectTimer = new Timer("ReconnectTimer", true);
        }

        return _reconnectTimer;
    }

    private void clearReconnectTimer() {
        if (_reconnectTimer != null) {
            Logger.d("Clearing reconnect timer");
            _reconnectTimer.cancel();
            _reconnectTimer = null;
        } else {
            Logger.d("Reconnect timer is already null");
        }
    }

    /**
     * Sets the desired SYNC and HMI display languages, and re-registers the
     * application.
     */
    public void resetLanguagesDesired(String appId, Language syncLanguageDesired,
                                      Language hmiDisplayLanguageDesired) {
        // TODO : Update RAI

        restartRPCProtocolSession(syncSession.getSessionIdByAppId(appId));
    }

    public boolean hasServiceInServicesPool(String appId, ServiceType serviceType) {
        return !syncSession.isServicesEmpty() && syncSession.hasService(appId, serviceType);
    }

    /**
     * Return number of Services in current Session
     *
     * @return number of Services in current Session
     */
    public int getServicesNumber() {
        return syncSession.getServicesNumber();
    }

    protected byte getSessionIdByAppId(String appId) {
        return syncSession.getSessionIdByAppId(appId);
    }

    protected String getAppIdBySessionId(byte sessionId) {
        return syncSession.getAppIdBySessionId(sessionId);
    }

    public void setAutoActivateIdReturned(String autoActivateIdDesired) {
        this._autoActivateIdReturned = autoActivateIdDesired;
    }

    public String getAutoActivateIdReturned() {
        return _autoActivateIdReturned;
    }

    public IRPCMessageHandler getRPCMessageHandler() {
        return rpcMessageHandler;
    }

    public void setRPCMessageHandler(IRPCMessageHandler RPCMessageHandler) {
        this.rpcMessageHandler = RPCMessageHandler;
    }

    private void endSession(byte sessionId) {
        String appId = syncSession.getAppIdBySessionId(sessionId);

        // In case End Service message received from SDL without UnregisterAppInterface response
        appIds.remove(appId);
        raiTable.remove(appId);

        stopAllServicesByAppId(appId);
        if (mSyncConnection != null) {
            mSyncConnection.closeSession(sessionId);
        }
        stopSession(appId);

        Logger.d(LOG_TAG + " End Session, sesId'sN:" + syncSession.getSessionIdsNumber());
        if (syncSession.getSessionIdsNumber() == 0) {
            closeSyncConnection(false);
        }
    }

    // TODO : Hide this method from public when no Test Cases are need
    /**
     * Initialize new Session. <b>In production this method MUST be private</b>
     */
    public void initializeSession(final String appId) {
        // Initialize a start session procedure
        Logger.d(LOG_TAG + " Init session, id:" + appId);

        final int sessionIdsNumber = syncSession.getSessionIdsNumber();
        if (_callbackToUIThread) {
            // Run in UI thread
            _mainUIHandler.post(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < sessionIdsNumber; i++) {
                        _proxyListener.onStartSession(appId);
                    }
                }
            });
        } else {
            for (int i = 0; i < sessionIdsNumber; i++) {
                _proxyListener.onStartSession(appId);
            }
        }

        syncSession.addAppId(appId);
        mSyncConnection.initialiseSession(Session.DEFAULT_SESSION_ID);
    }

    public static void isHeartbeatAck(boolean isHearBeatAck) {
        SyncProxyBase.heartBeatAck = isHearBeatAck;
    }

    // Private Class to Interface with SyncConnection
    public class SyncInterfaceBroker implements ISyncConnectionListener {

        @Override
        public void onTransportConnected() {
            for (String appId: appIds) {
                initializeSession(appId);
            }
        }

        @Override
        public void onTransportDisconnected(String info) {
            // proxyOnTransportDisconnect is called to alert the proxy that a requested
            // disconnect has completed

//			if (_advancedLifecycleManagementEnabled) {
//				// If ALM, nothing is required to be done here
//			} else {
            // If original model, notify app the proxy is closed so it will delete and reinstanciate
            notifyProxyClosed(info, new SyncException("Transport disconnected.", SyncExceptionCause.SYNC_UNAVAILALBE));
//			}
        }

        @Override
        public void onTransportError(String info, Exception e) {
            Logger.e("Transport failure: " + info, e);

            if (_transportConfig != null &&
                    _transportConfig.getTransportType() ==  TransportType.USB) {
                if (CommonUtils.isUSBNoSuchDeviceError(e.toString())) {

                    if (_callbackToUIThread) {
                        // Run in UI thread
                        _mainUIHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                _proxyListener.onUSBNoSuchDeviceException();
                            }
                        });
                    } else {
                        _proxyListener.onUSBNoSuchDeviceException();
                    }

                    try {
                        dispose();
                    } catch (SyncException e1) {
                        e1.printStackTrace();
                    }

                    return;
                }
            }

            if (_advancedLifecycleManagementEnabled) {
                // Cycle the proxy
                cycleProxy(SyncDisconnectedReason.TRANSPORT_ERROR);
            } else {
                notifyProxyClosed(info, e);
            }
        }

        @Override
        public void onHeartbeatTimedOut(byte sessionId) {
            String appId = syncSession.getAppIdBySessionId(sessionId);
            try {
                closeSession(appId);
            } catch (SyncException e) {
                Logger.e(LOG_TAG + " On HB Timeout:" + e.getMessage());
            }

            final String msg = "Heartbeat timeout";
            notifyProxyClosed(msg, new SyncException(msg, SyncExceptionCause.HEARTBEAT_PAST_DUE));
        }

        @Override
        public void onProtocolMessageReceived(ProtocolMessage msg) {
            // AudioPathThrough is coming WITH BulkData but WITHOUT JSON Data
            // Policy Snapshot is coming WITH BulkData and WITH JSON Data
            if ((msg.getData() != null && msg.getData().length > 0) ||
                    (msg.getBulkData() != null && msg.getBulkData().length > 0)) {
                queueIncomingMessage(msg);
            }
        }

        @Override
        public void onProtocolSessionStarted(byte sessionId, byte version) {
            String message = "RPC Session started, sessionId:" + sessionId +
                    ", protocol version:" + (int) version +
                    ", negotiated protocol version: " + getSyncConnection().getProtocolVersion();
            Logger.i(message);

            startProtocolSession(sessionId);

            mSyncConnection.setHeartbeatMonitor(
                    HeartbeatBuilder.buildHeartbeatMonitor(sessionId, heartBeatInterval,
                            heartBeatAck));
            mSyncConnection.startHeartbeatTimer(sessionId);
        }

        @Override
        public void onProtocolServiceEndedAck(ServiceType serviceType, byte sessionId) {
            Logger.i("EndServiceAck received serType:" + serviceType.getName() + " sesId:" + sessionId);
            handleEndServiceAck(serviceType, sessionId);
        }

        @Override
        public void onProtocolServiceEnded(ServiceType serviceType, byte sessionId) {
            Logger.i("EndService received serType:" + serviceType.getName() + " sesId:" + sessionId);
            handleEndService(serviceType, sessionId);
        }

        @Override
        public void onProtocolError(String info, Throwable e) {
            passErrorToProxyListener(info, e);
        }

        @Override
        public void onMobileNavAckReceived(byte sessionId, int frameReceivedNumber) {
            handleMobileNavAck(sessionId, frameReceivedNumber);
        }

        @Override
        public void onStartServiceNackReceived(byte sessionId, ServiceType serviceType) {
            handleStartServiceNack(sessionId, serviceType);
        }

        @Override
        public void onProtocolServiceStarted(ServiceType serviceType, byte sessionId, byte version) {
            if (mSyncConnection.getProtocolVersion() >= ProtocolConstants.PROTOCOL_VERSION_TWO) {
                if (serviceType.equals(ServiceType.Mobile_Nav)) {
                    onProtocolServiceStarted_MobileNavi(sessionId);
                } else if (serviceType.equals(ServiceType.Audio_Service)) {
                    onProtocolServiceStarted_Audio(sessionId);
                }
            }
        }

        @Override
        public void sendOutgoingMessage(ProtocolMessage protocolMessage) {
            queueOutgoingMessage(protocolMessage);
        }
    }

    public IRPCRequestConverterFactory getRpcRequestConverterFactory() {
        return rpcRequestConverterFactory;
    }

    public void setRpcRequestConverterFactory(
            IRPCRequestConverterFactory rpcRequestConverterFactory) {
        this.rpcRequestConverterFactory = rpcRequestConverterFactory;
    }

    protected void processRegisterAppInterfaceResponse(final byte sessionId,
                                                       final RegisterAppInterfaceResponse response) {

        final String appId = syncSession.getAppIdBySessionId(sessionId);

        if (!response.getSuccess()) {
            setHashId(appId, null);
        }

        if (response.getResultCode() == Result.RESUME_FAILED) {
            setHashId(appId, null);
        }

        // Create callback
        if (_callbackToUIThread) {
            // Run in UI thread
            _mainUIHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (_proxyListener instanceof IProxyListener) {
                        ((IProxyListener) _proxyListener).onRegisterAppInterfaceResponse(response);
                    } else if (_proxyListener instanceof IProxyListenerALMTesting) {
                        ((IProxyListenerALMTesting) _proxyListener)
                                .onRegisterAppInterfaceResponse(appId, response);
                    }
                }
            });
        } else {
            if (_proxyListener instanceof IProxyListener) {
                ((IProxyListener) _proxyListener).onRegisterAppInterfaceResponse(response);
            } else if (_proxyListener instanceof IProxyListenerALMTesting) {
                ((IProxyListenerALMTesting) _proxyListener)
                        .onRegisterAppInterfaceResponse(appId, response);
            }
        }
    }

    /**
     * Restore interrupted Services
     */
    public void restoreServices(String appId) {
        Logger.d(LOG_TAG + " Restore Services for appId:" + appId + " " + syncSession);
        if (!syncSession.isServicesEmpty() && mSyncConnection.getIsConnected()) {
            byte sessionId = syncSession.getSessionIdByAppId(appId);
            //Logger.d(LOG_TAG + " Restore Services for appId:" + appId);
            if (syncSession.hasService(appId, ServiceType.Mobile_Nav)) {
                mSyncConnection.startMobileNavService(sessionId);
            }
            if (syncSession.hasService(appId, ServiceType.Audio_Service)) {
                mSyncConnection.startAudioService(sessionId);
            }
        }
    }

    public IOnSystemRequestHandler getOnSystemRequestHandler() {
        return onSystemRequestHandler;
    }

    public void setOnSystemRequestHandler(IOnSystemRequestHandler onSystemRequestHandler) {
        this.onSystemRequestHandler = onSystemRequestHandler;
    }

    /**
     * Returns the next correlation ID used for internal messages.
     *
     * @return next correlation ID
     */
    private int nextCorrelationId() {
        return ++lastCorrelationId;
    }

    @Override
    public void putSystemFile(String appId, String filename, byte[] data, FileType fileType)
            throws SyncException {
        putSystemFile(appId, filename, data, null, fileType);
    }

    @Override
    public void putSystemFile(final String appId, String filename, byte[] data, Integer offset,
                              FileType fileType) throws SyncException {
        final int correlationID = nextCorrelationId();
        final PutFile putFile = RPCRequestFactory.buildPutFile(filename, fileType, null, data,
                correlationID);
        putFile.setSystemFile(true);
        if (offset != null) {
            putFile.setOffset(offset);
            putFile.setLength(data.length);
        }

        if (_callbackToUIThread) {
            // Run in UI thread
            _mainUIHandler.post(new Runnable() {
                @Override
                public void run() {
                    _proxyListener.onPutFileRequest(appId, putFile);
                }
            });
        } else {
            _proxyListener.onPutFileRequest(appId, putFile);
        }

        sendRPCRequest(appId, putFile);
        internalRequestCorrelationIDs.add(correlationID);
    }

    @Override
    public void putPolicyTableUpdateFile(String appId, String filename, byte[] data, FileType fileType,
                                         RequestType requestType)
            throws SyncException {
        final int correlationID = nextCorrelationId();

        if (fileType == FileType.BINARY) {
            PutFile putFile = RPCRequestFactory.buildPutFile(filename, FileType.JSON, null, data,
                    correlationID);

            sendRPCRequest(appId, putFile);
        } else if (fileType == FileType.JSON) {
            SystemRequest systemRequest = RPCRequestFactory.buildSystemRequest(filename, data,
                    correlationID, requestType);

            sendRPCRequest(appId, systemRequest);
        }

        internalRequestCorrelationIDs.add(correlationID);
    }

    /**
     * Process policy file snapshot request
     * @param snapshot bytes array of the data
     * @param fileType type of the file
     */
    private void processPolicyTableSnapshot(final String appId, final byte[] snapshot,
                                            final FileType fileType,
                                            final RequestType requestType) {
        Runnable request = new Runnable() {
            @Override
            public void run() {
                onSystemRequestHandler.onPolicyTableSnapshotRequest(appId, SyncProxyBase.this,
                        snapshot, fileType, requestType);
            }
        };
        if (_callbackToUIThread) {
            _mainUIHandler.post(request);
        } else {
            request.run();
        }
    }

    /**
     * Test Section
     */

    /**
     * Return an instance of the object of {@link com.ford.syncV4.test.TestConfig} type in order to
     * set or modify configuration necessary to perform SDK testing
     */
    public TestConfig getTestConfig() {
        return mTestConfig;
    }

    /**
     * Set a callback to perform test Cases of the SDK
     *
     * @param mTestConfigCallback callback function
     */
    public void setTestConfigCallback(ITestConfigCallback mTestConfigCallback) {
        this.mTestConfigCallback = mTestConfigCallback;
    }
}