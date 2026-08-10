from pathlib import Path

path = Path('app/src/main/java/com/liftly/app/ui/screens/ProfileScreen.kt')
text = path.read_text(encoding='utf-8')
old_import = 'import com.liftly.app.data.ExerciseEntity\n'
new_import = 'import com.liftly.app.BuildConfig\nimport com.liftly.app.data.ExerciseEntity\n'
if old_import not in text:
    raise RuntimeError('ProfileScreen import anchor missing')
text = text.replace(old_import, new_import, 1)
old = '            item { Spacer(Modifier.height(100.dp)) }\n'
new = '''            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        "LIFTLY ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "build ${BuildConfig.VERSION_CODE}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    )
                }
            }
            item { Spacer(Modifier.height(100.dp)) }
'''
if old not in text:
    raise RuntimeError('ProfileScreen footer anchor missing')
text = text.replace(old, new, 1)
path.write_text(text, encoding='utf-8')
print('Visible version label applied')
