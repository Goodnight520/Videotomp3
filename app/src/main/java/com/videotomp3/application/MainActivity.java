package com.videotomp3.application;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.content.*;
import android.net.*;
import android.media.*;
import android.provider.OpenableColumns;
import android.provider.MediaStore;
import android.database.Cursor;
import android.text.Html;
import java.io.*;
import java.nio.ByteBuffer;

public class MainActivity extends Activity {

    private static final int PICK_VIDEO = 1;
    private Uri videoUri;
    private TextView statusText;
    private Button btnToM4a;
    private Button btnToMp3;
    private Button btnSelect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. 初始化控件
        statusText = (TextView) findViewById(R.id.statusText);
        btnToM4a = (Button) findViewById(R.id.btnToM4a);
        btnToMp3 = (Button) findViewById(R.id.btnToMp3);
        btnSelect = (Button) findViewById(R.id.btnSelect);
        Button btnOpenFolder = (Button) findViewById(R.id.btnOpenFolder);
        TextView authorText = (TextView) findViewById(R.id.authorText);

        // 2. 选择视频按钮
        btnSelect.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
					intent.addCategory(Intent.CATEGORY_OPENABLE);
					intent.setType("video/*");
					startActivityForResult(intent, PICK_VIDEO);
				}
			});

        // 3. 提取 M4A
        btnToM4a.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (videoUri != null) {
						extractWithMediaStore("audio/mp4", ".m4a");
					}
				}
			});

        // 4. 提取 MP3
        btnToMp3.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (videoUri != null) {
						extractWithMediaStore("audio/mpeg", ".mp3");
					}
				}
			});

        // 5. 查看文件夹
        btnOpenFolder.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					openWithAppChooser();
				}
			});

        // 6. 底部彩色版权 + 跳转 B 站逻辑 (调整了字体大小和样式)
        if (authorText != null) {
            // 使用 <big> 放大作者行，用 <b> 加粗
            String styledText = "<font color='#4CAF50'>本软件完全开源且永久免费，严禁用于任何商业用途</font><br/>" +
				"<font color='#FF0000'>本软件受到 GPL 开源协议保护</font><br/>" +
				"<big><b><font color='#FFD700'>由 哔哩哔哩UP主：</font>" +
				"<u><font color='#007AFF'>我已被封</font></u>" +
				"<font color='#FFD700'> 开发</font></b></big>";

            authorText.setText(Html.fromHtml(styledText));

            authorText.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						try {
							String uid = "3537120750733918"; 
							Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("bilibili://space/" + uid));
							if (getPackageManager().queryIntentActivities(intent, 0).size() == 0) {
								intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://space.bilibili.com/" + uid));
							}
							startActivity(intent);
						} catch (Exception e) {
							Toast.makeText(MainActivity.this, "无法打开主页", Toast.LENGTH_SHORT).show();
						}
					}
				});
        }
    }

    private void openWithAppChooser() {
        try {
            Uri uri = Uri.parse("content://com.android.externalstorage.documents/document/primary:Music%2FVideoToAudio");
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setClassName("com.google.android.documentsui", "com.android.documentsui.files.FilesActivity");
            intent.setDataAndType(uri, "vnd.android.cursor.dir/document");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Uri folderUri = Uri.parse("content://com.android.externalstorage.documents/document/primary:Music%2FVideoToAudio");
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.putExtra("android.provider.extra.INITIAL_URI", folderUri);
                startActivity(intent);
            } catch (Exception e2) {
                Toast.makeText(this, "提取成功！请前往 Music 文件夹查看", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_VIDEO && resultCode == RESULT_OK && data != null) {
            videoUri = data.getData();
            getContentResolver().takePersistableUriPermission(videoUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

            String fileName = "未知文件";
            Cursor cursor = getContentResolver().query(videoUri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index != -1) fileName = cursor.getString(index);
                cursor.close();
            }

            statusText.setText(Html.fromHtml("已选择视频：<br><font color='red'><b>" + fileName + "</b></font><br>请选择下方格式开始提取"));
            btnToM4a.setEnabled(true);
            btnToMp3.setEnabled(true);
        }
    }

    private void extractWithMediaStore(final String mimeType, final String extension) {
        statusText.setText("正在极速提取...");
        btnSelect.setEnabled(false);
        btnToM4a.setEnabled(false);
        btnToMp3.setEnabled(false);

        new Thread(new Runnable() {
				@Override
				public void run() {
					MediaExtractor extractor = null;
					MediaMuxer muxer = null;
					ParcelFileDescriptor inPfd = null;
					ParcelFileDescriptor outPfd = null;

					try {
						String fileName = "Music_" + System.currentTimeMillis();
						Cursor cursor = getContentResolver().query(videoUri, null, null, null, null);
						if (cursor != null && cursor.moveToFirst()) {
							int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
							if (index != -1) {
								fileName = cursor.getString(index);
								if (fileName.contains(".")) {
									fileName = fileName.substring(0, fileName.lastIndexOf("."));
								}
							}
							cursor.close();
						}

						ContentValues values = new ContentValues();
						values.put(MediaStore.Audio.Media.DISPLAY_NAME, fileName + extension);
						values.put(MediaStore.Audio.Media.MIME_TYPE, mimeType);
						values.put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/VideoToAudio");

						Uri outputUri = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
						outPfd = getContentResolver().openFileDescriptor(outputUri, "w");
						inPfd = getContentResolver().openFileDescriptor(videoUri, "r");

						extractor = new MediaExtractor();
						extractor.setDataSource(inPfd.getFileDescriptor());

						int trackIndex = -1;
						for (int i = 0; i < extractor.getTrackCount(); i++) {
							if (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
								trackIndex = i;
								break;
							}
						}

						if (trackIndex >= 0) {
							extractor.selectTrack(trackIndex);
							muxer = new MediaMuxer(outPfd.getFileDescriptor(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
							int writeTrack = muxer.addTrack(extractor.getTrackFormat(trackIndex));
							muxer.start();

							ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
							MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
							while (true) {
								info.size = extractor.readSampleData(buffer, 0);
								if (info.size < 0) break;
								info.presentationTimeUs = extractor.getSampleTime();
								info.flags = extractor.getSampleFlags();
								muxer.writeSampleData(writeTrack, buffer, info);
								extractor.advance();
							}
							muxer.stop();
							updateUI("提取成功！请点击下方查看按钮");
						}
					} catch (Exception e) {
						updateUI("处理失败: " + e.getMessage());
					} finally {
						try {
							if (extractor != null) extractor.release();
							if (muxer != null) muxer.release();
							if (inPfd != null) inPfd.close();
							if (outPfd != null) outPfd.close();
						} catch (Exception e) {}
					}
				}
			}).start();
    }

    private void updateUI(final String msg) {
        runOnUiThread(new Runnable() {
				@Override
				public void run() {
					statusText.setText(msg);
					if (msg.contains("成功")) {
						Toast.makeText(MainActivity.this, "音频已保存至 Music/VideoToAudio", Toast.LENGTH_LONG).show();
					}
					btnSelect.setEnabled(true);
					btnToM4a.setEnabled(true);
					btnToMp3.setEnabled(true);
				}
			});
    }
}

