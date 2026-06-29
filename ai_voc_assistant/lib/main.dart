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

  runApp(const VocAssistantApp());
}

class VocAssistantApp extends StatelessWidget {
  const VocAssistantApp({super.key});

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
        ChangeNotifierProvider(create: (_) => DashboardViewModel(vocRepo, kbRepo)),
        ChangeNotifierProvider(
          create: (ctx) => AiViewModel(
            kbRepo,
            vocRepo,
            ctx.read<SettingsViewModel>(),
          ),
        ),
        ChangeNotifierProvider(create: (_) => KnowledgeBaseViewModel(kbRepo)),
        ChangeNotifierProvider(
          create: (ctx) => JiraViewModel(ctx.read<SettingsViewModel>()),
        ),
        ChangeNotifierProvider(
          create: (ctx) => IntegrationViewModel(
            vocRepo,
            ctx.read<SettingsViewModel>(),
          ),
        ),
      ],
      child: Consumer<SettingsViewModel>(
        builder: (context, settingsVm, _) {
          return MaterialApp(
            title: 'AI VOC Assistant',
            debugShowCheckedModeBanner: false,
            theme: AppTheme.lightTheme,
            darkTheme: AppTheme.darkTheme,
            themeMode: settingsVm.themeMode,
            home: const SplashScreen(),
          );
        },
      ),
    );
  }
}
