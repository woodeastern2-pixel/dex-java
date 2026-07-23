import 'dart:io';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

import 'core/database/database_helper.dart';
import 'core/theme/app_theme.dart';
import 'data/datasources/local/voc_local_datasource.dart';
import 'data/datasources/local/knowledge_base_local_datasource.dart';
import 'data/datasources/local/settings_local_datasource.dart';
import 'data/repositories/voc_repository_impl.dart';
import 'data/repositories/knowledge_base_repository_impl.dart';
import 'data/repositories/settings_repository_impl.dart';
import 'data/services/in_app_sync_receiver_service.dart';
import 'presentation/viewmodels/voc_viewmodel.dart';
import 'presentation/viewmodels/dashboard_viewmodel.dart';
import 'presentation/viewmodels/ai_viewmodel.dart';
import 'presentation/viewmodels/knowledge_base_viewmodel.dart';
import 'presentation/viewmodels/jira_viewmodel.dart';
import 'presentation/viewmodels/settings_viewmodel.dart';
import 'presentation/viewmodels/integration_viewmodel.dart';
import 'presentation/screens/splash/splash_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Desktop: FFI 초기화
  if (Platform.isWindows || Platform.isLinux || Platform.isMacOS) {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
  }

  await DatabaseHelper.instance.database;

  final settingsLocalDs = SettingsLocalDatasource(DatabaseHelper.instance);
  final settingsRepo = SettingsRepositoryImpl(settingsLocalDs);
  await InAppSyncReceiverService.instance.start(
    settingsRepository: settingsRepo,
  );

  runApp(const VocAssistantApp());
}

class VocAssistantApp extends StatelessWidget {
  const VocAssistantApp({super.key});

  static final GlobalKey<ScaffoldMessengerState> _messengerKey =
      GlobalKey<ScaffoldMessengerState>();

  static void _showInboundSyncSnackBar(String message) {
    final state = _messengerKey.currentState;
    if (state == null) return;
    state.showSnackBar(
      SnackBar(
        content: Text(message),
        duration: const Duration(seconds: 3),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final dbHelper = DatabaseHelper.instance;

    final vocLocalDs = VocLocalDatasource(dbHelper);
    final kbLocalDs = KnowledgeBaseLocalDatasource(dbHelper);
    final settingsLocalDs = SettingsLocalDatasource(dbHelper);

    final vocRepo = VocRepositoryImpl(vocLocalDs);
    final kbRepo = KnowledgeBaseRepositoryImpl(kbLocalDs);
    final settingsRepo = SettingsRepositoryImpl(settingsLocalDs);

    return MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => SettingsViewModel(settingsRepo)),
        ChangeNotifierProvider(create: (_) => VocViewModel(vocRepo)),
        ChangeNotifierProvider(
          create: (ctx) => DashboardViewModel(
            vocRepo,
            kbRepo,
            ctx.read<SettingsViewModel>(),
          ),
        ),
        ChangeNotifierProvider(
          create: (ctx) => AiViewModel(
            kbRepo,
            vocRepo,
            ctx.read<SettingsViewModel>(),
          ),
        ),
        ChangeNotifierProvider(
          create: (ctx) => KnowledgeBaseViewModel(
            kbRepo,
            ctx.read<SettingsViewModel>(),
          ),
        ),
        ChangeNotifierProvider(
          create: (ctx) => JiraViewModel(ctx.read<SettingsViewModel>()),
        ),
        ChangeNotifierProvider(
          create: (ctx) => IntegrationViewModel(
            vocRepo,
            ctx.read<SettingsViewModel>(),
            onInboundSyncEvent: _showInboundSyncSnackBar,
          ),
        ),
      ],
      child: Consumer<SettingsViewModel>(
        builder: (context, settingsVm, _) {
          return MaterialApp(
            title: 'AI VOC Assistant',
            debugShowCheckedModeBanner: false,
            scaffoldMessengerKey: _messengerKey,
            theme: AppTheme.lightTheme,
            darkTheme: AppTheme.darkTheme,
            themeMode: settingsVm.themeMode,
            builder: (context, child) {
              final mediaQuery = MediaQuery.of(context);
              return MediaQuery(
                data: mediaQuery.copyWith(
                  textScaler: TextScaler.linear(settingsVm.textScaleFactor),
                ),
                child: SelectionArea(
                  child: child ?? const SizedBox.shrink(),
                ),
              );
            },
            home: const SplashScreen(),
          );
        },
      ),
    );
  }
}
