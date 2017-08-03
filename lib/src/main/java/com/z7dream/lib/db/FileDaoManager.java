package com.z7dream.lib.db;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.z7dream.lib.callback.Callback;
import com.z7dream.lib.db.bean.FileInfo;
import com.z7dream.lib.db.bean.FileInfo_;
import com.z7dream.lib.db.bean.FileStarInfo;
import com.z7dream.lib.db.bean.FileStarInfo_;
import com.z7dream.lib.db.bean.FileTypeInfo;
import com.z7dream.lib.db.bean.FileTypeInfo_;
import com.z7dream.lib.model.Extension;
import com.z7dream.lib.model.MagicFileEntity;
import com.z7dream.lib.model.MagicPicEntity1;
import com.z7dream.lib.service.FileUpdatingService;
import com.z7dream.lib.tool.CacheManager;
import com.z7dream.lib.tool.EnumFileType;
import com.z7dream.lib.tool.FileUtils;
import com.z7dream.lib.tool.rx.RxSchedulersHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import io.objectbox.Box;
import io.objectbox.BoxStore;
import io.objectbox.query.QueryBuilder;
import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;

import static com.z7dream.lib.tool.MagicExplorer.QQ_FILE_PATH;
import static com.z7dream.lib.tool.MagicExplorer.QQ_PIC_PATH;
import static com.z7dream.lib.tool.MagicExplorer.WX_FILE_PATH;
import static com.z7dream.lib.tool.MagicExplorer.WX_PIC_PATH;


/**
 * Created by Z7Dream on 2017/7/4 11:36.
 * Email:zhangxyfs@126.com
 */

public class FileDaoManager implements FileDaoImpl {
    private Box<FileInfo> fileInfoBox;
    private Box<FileTypeInfo> fileTypeInfoBox;
    private Box<FileStarInfo> fileStarInfoBox;
    private BoxStore boxStore;

    private static String PUT_IN_STORAGE_PATH;

    public FileDaoManager(Context context, BoxStore boxStore) {
        PUT_IN_STORAGE_PATH = CacheManager.getSystemPicCachePath(context) + "addAllFile.succ";
        fileInfoBox = boxStore.boxFor(FileInfo.class);
        fileTypeInfoBox = boxStore.boxFor(FileTypeInfo.class);
        fileStarInfoBox = boxStore.boxFor(FileStarInfo.class);
        this.boxStore = boxStore;
    }

    private String getUserToken() {
        return FileUpdatingService.getConfigCallback().getConfig().userToken;
    }

    @Override
    public Map<String, String> getStarMap() {
        Map<String, String> map = new HashMap<>();
        List<FileStarInfo> list = getStarList();
        for (int i = 0; i < list.size(); i++) {
            map.put(list.get(i).getFilePath(), list.get(i).getFilePath());
        }
        return map;
    }

    /**
     * 获取星标文件列表
     *
     * @return
     */
    @Override
    public List<FileStarInfo> getStarList() {
        return fileStarInfoBox.query().equal(FileStarInfo_.userToken, getUserToken()).build().find();
    }

    /**
     * 获取星标文件列表
     *
     * @param page 页码(从0开始)
     * @param size 数量
     * @return
     */
    @Override
    public List<FileStarInfo> getStarList(int page, int size) {
        return fileStarInfoBox.query().equal(FileStarInfo_.userToken, getUserToken()).build().find(page * size, size);
    }

    /**
     * 添加星标
     *
     * @param filePathList
     */
    @Override
    public void addStar(List<String> filePathList) {
        List<String> newFilePathList = new ArrayList<>();
        newFilePathList.addAll(filePathList);
        List<FileStarInfo> list = fileStarInfoBox.query().build().find();
        List<String> tempList = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            tempList.add(list.get(i).getFilePath());
        }
        newFilePathList.removeAll(tempList);
        List<FileStarInfo> newInstertList = new ArrayList<>();
        for (int i = 0; i < newFilePathList.size(); i++) {
            FileStarInfo info = new FileStarInfo();
            info.setUserToken(getUserToken());
            info.setFilePath(newFilePathList.get(i));
            newInstertList.add(info);
        }
        boxStore.runInTx(() -> fileStarInfoBox.put(newInstertList));
    }

    /**
     * 删除星标
     *
     * @param filePathList
     */
    @Override
    public void removeStar(List<String> filePathList) {
        QueryBuilder<FileStarInfo> queryBuilder = fileStarInfoBox.query();
        for (int i = 0; i < filePathList.size(); i++) {
            queryBuilder.equal(FileStarInfo_.filePath, filePathList.get(i));
            if (i < filePathList.size() - 1) {
                queryBuilder.or();
            }
        }
        List<FileStarInfo> list = queryBuilder.build().find();
        boxStore.runInTx(() -> fileStarInfoBox.remove(list));
    }

    /**
     * 添加文件类型
     *
     * @param typeName 文件类型名字
     * @param format   格式
     */
    @Override
    public void addFileTypeInfo(String typeName, String format) {
        fileTypeInfoBox.put(new FileTypeInfo(typeName, format));
    }

    /**
     * 文件入库操作
     */
    @Override
    public void toPutFileInStorage(int folderSize) {
        File check = new File(PUT_IN_STORAGE_PATH);
        long count = fileInfoBox.query().build().count();
        if (check.exists()) {
            if (count > folderSize) {
                toUpdateFileInfoDB();
                return;
            }
            check.delete();
        }
        fileInfoBox.removeAll();
        fileTypeInfoBox.removeAll();

        Map<String, String> typeMap = initFileType();
        Observable.create((ObservableOnSubscribe<List<FileInfo>>) e -> {
            File folder = new File(CacheManager.getSaveFilePath());
            Stack<String> stack = new Stack<>();
            stack.push(folder.getPath());
            List<FileInfo> tempFileList = new ArrayList<>();

            while (!stack.isEmpty()) {
                String temp = stack.pop();
                File path = new File(temp);
                File[] files = path.listFiles();
                if (null == files)
                    continue;
                for (File f : files) {
                    // 递归监听目录
                    if (FileUtils.isNeedToListener(f)) {
                        if (f.isDirectory()) {
                            stack.push(f.getAbsolutePath());
                        }
                        FileInfo fileInfo = new FileInfo();
                        fileInfo.setFileName(f.getName());
                        fileInfo.setFilePath(f.getAbsolutePath());
                        fileInfo.setParentPath(f.getParent());
                        fileInfo.setExtension(FileUtils.getExtensionName(fileInfo.getFileName()));
                        fileInfo.setLastModifyTime(f.lastModified());
                        fileInfo.setFileSize(f.length());
                        fileInfo.setIsFile(f.isFile());

                        String fileType = typeMap.get(fileInfo.getExtension());
                        fileInfo.setFileType(fileType == null ? EnumFileType.OTHER.name() : fileType);
                        tempFileList.add(fileInfo);
                    }
                }
            }
            e.onNext(tempFileList);
            e.onComplete();
        }).compose(RxSchedulersHelper.io())
                .doOnComplete(() -> {
                    Log.d(getClass().getName(), "insert file succ");
                    check.createNewFile();
                })
                .subscribe(list -> boxStore.runInTxAsync(() -> fileInfoBox.put(list), (aVoid, throwable) -> list.clear())
                        , Throwable::printStackTrace);
    }

    /**
     * 是否入库完成
     *
     * @return
     */
    @Override
    public boolean isPutFileInStorageSucc() {
        return new File(PUT_IN_STORAGE_PATH).exists();
    }


    /**
     * 根据文件路径查询文件信息
     *
     * @param filePath
     * @return
     */
    @Override
    public FileInfo findFileInfoByPath(String filePath) {
        return fileInfoBox.query().equal(FileInfo_.filePath, filePath).build().findUnique();
    }

    /**
     * 添加文件信息
     *
     * @param f 文件
     */
    @Override
    public void addFileInfo(File f) {
        FileInfo fi = findFileInfoByPath(f.getAbsolutePath());
        if (fi == null) {
            if (f.isFile() && !isNeedFile(FileUtils.getExtensionName(f.getPath()))) {
                return;
            }

            FileInfo fileInfo = new FileInfo();
            fileInfo.setFileName(f.getName());
            fileInfo.setFilePath(f.getAbsolutePath());
            fileInfo.setParentPath(f.getParent());
            fileInfo.setExtension(FileUtils.getExtensionName(fileInfo.getFileName()));
            fileInfo.setLastModifyTime(f.lastModified());
            fileInfo.setFileSize(f.length());
            fileInfo.setIsFile(f.isFile());
            fileInfo.setFileType(getFileType(fileInfo.getExtension()));
            fileInfoBox.put(fileInfo);
        }
    }

    /**
     * 删除文件信息
     *
     * @param filePath 文件路径
     */
    @Override
    public void removeFileInfo(String filePath) {
        FileInfo fi = findFileInfoByPath(filePath);
        if (fi != null) {
            fileInfoBox.remove(fi);
        }
    }

    /**
     * 更新文件信息
     *
     * @param f 文件
     */
    @Override
    public void updateFileInfo(File f) {
        FileInfo fi = findFileInfoByPath(f.getAbsolutePath());
        if (fi != null) {
            fi.setFileName(f.getName());
            fi.setFilePath(f.getAbsolutePath());
            fi.setParentPath(f.getParent());
            fi.setExtension(FileUtils.getExtensionName(fi.getFileName()));
            fi.setLastModifyTime(f.lastModified());
            fi.setFileSize(f.length());
            fi.setIsFile(f.isFile());
            fi.setFileType(getFileType(fi.getExtension()));
            fileInfoBox.put(fi);
        }
    }

    /**
     * 获取文件类型
     *
     * @param exc 文件扩展名
     * @return
     */
    @Override
    public String getFileType(String exc) {
        FileTypeInfo info = fileTypeInfoBox.query().equal(FileTypeInfo_.format, exc.toLowerCase()).build().findFirst();
        if (info == null) {
            info = fileTypeInfoBox.query().equal(FileTypeInfo_.typeName, EnumFileType.OTHER.name()).build().findFirst();
        }
        if (info != null) {
            return info.getTypeName();
        }
        return EnumFileType.OTHER.name();
    }

    @Override
    public Map<String, String> getFileTypeMap() {
        Map<String, String> map = new HashMap<>();
        List<FileTypeInfo> list = fileTypeInfoBox.query().build().find();
        for (int i = 0; i < list.size(); i++) {
            map.put(list.get(i).getFormat(), list.get(i).getTypeName());
        }
        return map;
    }

    /**
     * 是否为需要的文件
     *
     * @param exc 文件扩展名
     * @return
     */
    @Override
    public boolean isNeedFile(String exc) {
        return fileTypeInfoBox.query().equal(FileTypeInfo_.format, exc.toLowerCase()).build().findFirst() != null;
    }

    /**
     * 根据文件类型查找
     *
     * @param enumFileType
     * @return
     */
    @Override
    public List<FileInfo> getFileInfoList(EnumFileType enumFileType) {
        QueryBuilder<FileInfo> queryBuilder = fileInfoBox.query()
                .equal(FileInfo_.isFile, true);

        if (enumFileType != EnumFileType.ALL) {
            queryBuilder.equal(FileInfo_.fileType, enumFileType.name());
        }
        if (enumFileType == EnumFileType.PIC) {
            queryBuilder.greater(FileInfo_.fileSize, 10 * 1024);//图片至少要大于10kb
        }
        return queryBuilder.build().find();
    }

    /**
     * 根据文件类型查找
     *
     * @param enumFileType 文件类型枚举
     * @param page         页码
     * @param size         数量
     * @return
     */
    @Override
    public List<FileInfo> getFileInfoList(EnumFileType enumFileType, int page, int size) {
        QueryBuilder<FileInfo> queryBuilder = fileInfoBox.query()
                .equal(FileInfo_.isFile, true);

        if (enumFileType != EnumFileType.ALL) {
            queryBuilder.equal(FileInfo_.fileType, enumFileType.name());
        }
        if (enumFileType == EnumFileType.PIC) {
            queryBuilder.greater(FileInfo_.fileSize, 10 * 1024);//图片至少要大于10kb
        }
        return queryBuilder.build().find(page * size, size);
    }

    /**
     * 根据文件类型模糊查找
     *
     * @param enumFileType
     * @return
     */
    @Override
    public List<FileInfo> getFileInfoList(EnumFileType enumFileType, String likeStr) {
        QueryBuilder<FileInfo> queryBuilder = fileInfoBox.query()
                .equal(FileInfo_.isFile, true);

        if (enumFileType != EnumFileType.ALL) {
            queryBuilder.equal(FileInfo_.fileType, enumFileType.name());
        }
        if (!TextUtils.isEmpty(likeStr)) {
            queryBuilder.contains(FileInfo_.filePath, likeStr);
        }
        if (enumFileType == EnumFileType.PIC) {
            queryBuilder.greater(FileInfo_.fileSize, 10 * 1024);//图片至少要大于10kb
        }
        return queryBuilder.build().find();
    }

    /**
     * 根据文件类型查找
     *
     * @param enumFileType 文件类型枚举
     * @param likeStr      模糊查询
     * @param page         页码
     * @param size         数量
     * @return
     */
    @Override
    public List<FileInfo> getFileInfoList(EnumFileType enumFileType, String likeStr, int page, int size) {
        QueryBuilder<FileInfo> queryBuilder = fileInfoBox.query()
                .equal(FileInfo_.isFile, true);

        if (enumFileType != EnumFileType.ALL) {
            queryBuilder.equal(FileInfo_.fileType, enumFileType.name());
        }
        if (!TextUtils.isEmpty(likeStr)) {
            queryBuilder.contains(FileInfo_.filePath, likeStr);
        }
        if (enumFileType == EnumFileType.PIC) {
            queryBuilder.greater(FileInfo_.fileSize, 10 * 1024);//图片至少要大于10kb
        }
        return queryBuilder.build().find(page * size, size);
    }

    /**
     * 获取qq文件列表
     *
     * @param enumFileType 文件类型枚举
     * @return
     */
    @Override
    public List<FileInfo> getQQFileInfoList(EnumFileType enumFileType) {
        QueryBuilder<FileInfo> queryBuilder = fileInfoBox.query()
                .equal(FileInfo_.isFile, true)
                .contains(FileInfo_.filePath, QQ_PIC_PATH)
                .or()
                .contains(FileInfo_.filePath, QQ_FILE_PATH)
                .orderDesc(FileInfo_.lastModifyTime);

        if (enumFileType != EnumFileType.ALL) {
            queryBuilder.equal(FileInfo_.fileType, enumFileType.name());
        }

        return queryBuilder.build().find();
    }

    @Override
    public List<FileInfo> getQQFileInfoList(EnumFileType enumFileType, int page, int size) {
        QueryBuilder<FileInfo> queryBuilder = fileInfoBox.query()
                .equal(FileInfo_.isFile, true)
                .contains(FileInfo_.filePath, QQ_PIC_PATH)
                .or()
                .contains(FileInfo_.filePath, QQ_FILE_PATH)
                .orderDesc(FileInfo_.lastModifyTime);

        if (enumFileType != EnumFileType.ALL) {
            queryBuilder.equal(FileInfo_.fileType, enumFileType.name());
        }

        return queryBuilder.build().find(page * size, size);
    }

    /**
     * 获取微信文件列表
     *
     * @param enumFileType 文件类型枚举
     * @return
     */
    @Override
    public List<FileInfo> getWXFileInfoList(EnumFileType enumFileType) {
        QueryBuilder<FileInfo> queryBuilder = fileInfoBox.query()
                .equal(FileInfo_.isFile, true)
                .contains(FileInfo_.filePath, WX_PIC_PATH)
                .or()
                .contains(FileInfo_.filePath, WX_FILE_PATH)
                .orderDesc(FileInfo_.lastModifyTime);

        if (enumFileType != EnumFileType.ALL) {
            queryBuilder.equal(FileInfo_.fileType, enumFileType.name());
        }

        return queryBuilder.build().find();
    }

    @Override
    public List<FileInfo> getWXFileInfoList(EnumFileType enumFileType, int page, int size) {
        QueryBuilder<FileInfo> queryBuilder = fileInfoBox.query()
                .equal(FileInfo_.isFile, true)
                .contains(FileInfo_.filePath, WX_PIC_PATH)
                .or()
                .contains(FileInfo_.filePath, WX_FILE_PATH)
                .orderDesc(FileInfo_.lastModifyTime);

        if (enumFileType != EnumFileType.ALL) {
            queryBuilder.equal(FileInfo_.fileType, enumFileType.name());
        }

        return queryBuilder.build().find(page * size, size);
    }

    /**
     * 获取最近30天文件列表
     *
     * @param enumFileType 文件类型枚举
     * @return
     */
    @Override
    public List<FileInfo> get30DaysFileInfoList(EnumFileType enumFileType) {
        long nowTime = System.currentTimeMillis();
        long needTime = nowTime - (30 * 24 * 60 * 60) * 1000L;

        QueryBuilder<FileInfo> queryBuilder = fileInfoBox.query()
                .equal(FileInfo_.isFile, true)
                .greater(FileInfo_.lastModifyTime, needTime)
                .notEqual(FileInfo_.extension, "")
                .orderDesc(FileInfo_.lastModifyTime);

        if (enumFileType != EnumFileType.ALL) {
            queryBuilder.equal(FileInfo_.fileType, enumFileType.name());
        }
        return queryBuilder.build().find();
    }

    /**
     * 获取最近30天文件列表
     *
     * @param enumFileType 文件类型枚举
     * @param page         页码
     * @param size         数量
     * @return
     */
    @Override
    public List<FileInfo> get30DaysFileInfoList(EnumFileType enumFileType, int page, int size) {
        long nowTime = System.currentTimeMillis();
        long needTime = nowTime - (30 * 24 * 60 * 60) * 1000L;

        QueryBuilder<FileInfo> queryBuilder = fileInfoBox.query()
                .equal(FileInfo_.isFile, true)
                .greater(FileInfo_.lastModifyTime, needTime)
                .notEqual(FileInfo_.extension, "")
                .orderDesc(FileInfo_.lastModifyTime);

        if (enumFileType != EnumFileType.ALL) {
            queryBuilder.equal(FileInfo_.fileType, enumFileType.name());
        }
        return queryBuilder.build().find(page * size, size);
    }

    /**
     * 获取图片文件夹列表
     *
     * @param callback
     */
    @Override
    public void getFilePicFolderList(Callback<ArrayList<MagicPicEntity1>> callback) {
        ArrayList<MagicPicEntity1> dataList = new ArrayList<>();
        Map<String, Integer> folderItemPosMap = new HashMap<>();
        List<MagicFileEntity> allPicList = new ArrayList<>();

        String notEquals1 = CacheManager.getCachePath(null, CacheManager.ES);
        String notEquals2 = CacheManager.getCachePath(null, CacheManager.PIC);
        String notEquals3 = CacheManager.getCachePath(null, CacheManager.PIC_TEMP);

        notEquals1 = notEquals1.endsWith(File.separator) ? notEquals1.substring(0, notEquals1.length() - 1) : notEquals1;
        notEquals2 = notEquals2.endsWith(File.separator) ? notEquals2.substring(0, notEquals2.length() - 1) : notEquals2;
        notEquals3 = notEquals3.endsWith(File.separator) ? notEquals3.substring(0, notEquals3.length() - 1) : notEquals3;

        List<FileInfo> list = fileInfoBox.query()
                .equal(FileInfo_.fileType, EnumFileType.PIC.name())
                .equal(FileInfo_.isFile, true)
                .notEqual(FileInfo_.parentPath, notEquals1)
                .notEqual(FileInfo_.parentPath, notEquals2)
                .notEqual(FileInfo_.parentPath, notEquals3)
                .orderDesc(FileInfo_.lastModifyTime)
                .build().find();

        Observable.fromIterable(list)
                .compose(RxSchedulersHelper.io())
                .doOnComplete(() -> {
                    MagicPicEntity1 itemEntity = new MagicPicEntity1();
                    itemEntity.name = "ALL";
                    itemEntity.icon = allPicList.size() > 0 ? allPicList.get(0).path : "";
                    itemEntity.path = "";
                    itemEntity.childNum = allPicList.size();
                    itemEntity.childList = new ArrayList<>();
                    itemEntity.childList.addAll(allPicList);
                    dataList.add(0, itemEntity);

                    callback.callListener(dataList);
                })
                .subscribe(info -> {
                    String parentName = FileUtils.getFolderName(info.getParentPath());

                    //生成所有图片
                    MagicFileEntity allPicChild = new MagicFileEntity();
                    allPicChild.name = info.getFileName();
                    allPicChild.size = info.getFileSize();
                    allPicChild.path = info.getFilePath();
                    allPicChild.time = info.getLastModifyTime();
                    allPicChild.isFile = true;
                    allPicList.add(allPicChild);

                    if (folderItemPosMap.get(parentName) == null) {
                        folderItemPosMap.put(parentName, dataList.size());
                    }

                    if (dataList.size() < folderItemPosMap.get(parentName) + 1) {
                        MagicPicEntity1 itemEntity = new MagicPicEntity1();
                        itemEntity.name = parentName;
                        itemEntity.icon = info.getFilePath();
                        itemEntity.path = info.getParentPath();
                        if (TextUtils.equals(parentName, "EB_photo")) {
                            File file1 = new File(info.getParentPath());
                            itemEntity.childNum = file1.list().length;

                            itemEntity.childList = new ArrayList<>();
                            for (int childPos = 0; childPos < file1.list().length; childPos++) {
                                File childFile = file1.listFiles()[childPos];

                                MagicFileEntity child = new MagicFileEntity();
                                child.name = childFile.getName();
                                child.size = childFile.length();
                                child.path = childFile.getPath();
                                child.time = childFile.lastModified();
                                child.isFile = true;
                                itemEntity.childList.add(child);
                            }

                            itemEntity.icon = itemEntity.childList.get(0).path;

                        } else {
                            itemEntity.childNum = 1;

                            itemEntity.childList = new ArrayList<>();
                            MagicFileEntity firstChild = new MagicFileEntity();
                            firstChild.name = info.getFileName();
                            firstChild.size = info.getFileSize();
                            firstChild.path = info.getFilePath();
                            firstChild.time = info.getLastModifyTime();
                            firstChild.isFile = true;
                            itemEntity.childList.add(firstChild);
                        }
                        dataList.add(itemEntity);
                    } else {
                        if (!TextUtils.equals(parentName, "EB_photo")) {
                            dataList.get(folderItemPosMap.get(parentName)).childNum += 1;

                            MagicFileEntity child = new MagicFileEntity();
                            child.name = info.getFileName();
                            child.size = info.getFileSize();
                            child.path = info.getFilePath();
                            child.time = info.getLastModifyTime();
                            child.isFile = true;
                            dataList.get(folderItemPosMap.get(parentName)).childList.add(child);
                        }
                    }

                }, error -> {
                });
    }

    @Override
    public void getAllCount(Callback<long[]> callback) {
        Observable.create((ObservableOnSubscribe<long[]>) e -> {
            long[] newNumbers = new long[9];
            newNumbers[0] = getCountByType(EnumFileType.PIC);
            newNumbers[1] = getCountByType(EnumFileType.AUDIO);
            newNumbers[2] = getCountByType(EnumFileType.VIDEO);
            newNumbers[3] = getCountByType(EnumFileType.TXT);
            newNumbers[4] = getCountByType(EnumFileType.EXCEL);
            newNumbers[5] = getCountByType(EnumFileType.PPT);
            newNumbers[6] = getCountByType(EnumFileType.WORD);
            newNumbers[7] = getCountByType(EnumFileType.PDF);
            newNumbers[8] = getCountByType(EnumFileType.OTHER);
            e.onNext(newNumbers);
            e.onComplete();
        }).compose(RxSchedulersHelper.io())
                .subscribe(callback::callListener, error -> callback.callListener(new long[9]));
    }

    @Override
    public long getCountByType(EnumFileType enumFileType) {
        QueryBuilder<FileInfo> queryBuilder = fileInfoBox.query()
                .equal(FileInfo_.isFile, true);

        if (enumFileType != EnumFileType.ALL) {
            queryBuilder.equal(FileInfo_.fileType, enumFileType.name());
        }
        return queryBuilder.build().count();
    }

    @Override
    public void destory() {
        fileInfoBox = null;
        fileTypeInfoBox = null;
        fileStarInfoBox = null;
        boxStore = null;
    }


    /**
     * 初始化 文件类型数据
     */
    private Map<String, String> initFileType() {
        Map<String, String> map = new HashMap<>();
        FileTypeInfo first = fileTypeInfoBox.query().build().findFirst();
        if (first == null) {
            List<FileTypeInfo> list = new ArrayList<>();
            for (int i = 0; i < Extension.PIC.length; i++) {
                FileTypeInfo info = new FileTypeInfo();
                info.setId(list.size());
                info.setTypeName(EnumFileType.PIC.name());
                info.setFormat(Extension.PIC[i]);
                list.add(info);
                map.put(Extension.PIC[i], EnumFileType.PIC.name());
            }
            for (int i = 0; i < Extension.TXT.length; i++) {
                FileTypeInfo info = new FileTypeInfo();
                info.setId(list.size());
                info.setTypeName(EnumFileType.TXT.name());
                info.setFormat(Extension.TXT[i]);
                list.add(info);
                map.put(Extension.TXT[i], EnumFileType.TXT.name());
            }
            for (int i = 0; i < Extension.EXCEL.length; i++) {
                FileTypeInfo info = new FileTypeInfo();
                info.setId(list.size());
                info.setTypeName(EnumFileType.EXCEL.name());
                info.setFormat(Extension.EXCEL[i]);
                list.add(info);
                map.put(Extension.EXCEL[i], EnumFileType.EXCEL.name());
            }
            for (int i = 0; i < Extension.PPT.length; i++) {
                FileTypeInfo info = new FileTypeInfo();
                info.setId(list.size());
                info.setTypeName(EnumFileType.PPT.name());
                info.setFormat(Extension.PPT[i]);
                list.add(info);
                map.put(Extension.PPT[i], EnumFileType.PPT.name());
            }
            for (int i = 0; i < Extension.WORD.length; i++) {
                FileTypeInfo info = new FileTypeInfo();
                info.setId(list.size());
                info.setTypeName(EnumFileType.WORD.name());
                info.setFormat(Extension.WORD[i]);
                list.add(info);
                map.put(Extension.WORD[i], EnumFileType.WORD.name());
            }
            for (int i = 0; i < Extension.PDF.length; i++) {
                FileTypeInfo info = new FileTypeInfo();
                info.setId(list.size());
                info.setTypeName(EnumFileType.PDF.name());
                info.setFormat(Extension.PDF[i]);
                list.add(info);
                map.put(Extension.PDF[i], EnumFileType.PDF.name());
            }
            for (int i = 0; i < Extension.AUDIO.length; i++) {
                FileTypeInfo info = new FileTypeInfo();
                info.setId(list.size());
                info.setTypeName(EnumFileType.AUDIO.name());
                info.setFormat(Extension.AUDIO[i]);
                list.add(info);
                map.put(Extension.AUDIO[i], EnumFileType.AUDIO.name());
            }
            for (int i = 0; i < Extension.VIDEO.length; i++) {
                FileTypeInfo info = new FileTypeInfo();
                info.setId(list.size());
                info.setTypeName(EnumFileType.VIDEO.name());
                info.setFormat(Extension.VIDEO[i]);
                list.add(info);
                map.put(Extension.VIDEO[i], EnumFileType.VIDEO.name());
            }
            for (int i = 0; i < Extension.ZIP.length; i++) {
                FileTypeInfo info = new FileTypeInfo();
                info.setId(list.size());
                info.setTypeName(EnumFileType.ZIP.name());
                info.setFormat(Extension.ZIP[i]);
                list.add(info);
                map.put(Extension.ZIP[i], EnumFileType.ZIP.name());
            }
            FileTypeInfo info = new FileTypeInfo();
            info.setId(list.size());
            info.setTypeName(EnumFileType.OTHER.name());
            info.setFormat("");
            list.add(info);
            map.put("", EnumFileType.OTHER.name());

            fileTypeInfoBox.put(list);
        }
        return map;
    }

    /**
     * 手动更新数据库数据，仅仅当应用启动时候去做
     */
    private void toUpdateFileInfoDB() {
        Observable.create((ObservableOnSubscribe<List<FileInfo>>) e -> {
            List<FileInfo> list = fileInfoBox.query().equal(FileInfo_.isFile, false).orderDesc(FileInfo_.lastModifyTime).build().find();
            List<Long> deleteFileInfoIdList = new ArrayList<>();
            List<FileInfo> needUpdateFileidList = new ArrayList<>();
            for (FileInfo folderInfo : list) {
                File file = new File(folderInfo.getFilePath());
                if (!file.exists()) {
                    deleteFileInfoIdList.add(folderInfo.getId());
                } else {
                    if (folderInfo.getLastModifyTime() != file.lastModified()) {//当前文件夹，时间不同，需要更新
                        needUpdateFileidList.add(folderInfo);
                        traverseChild(folderInfo.getFilePath(), deleteFileInfoIdList, needUpdateFileidList);
                    }
                }
            }
            fileInfoBox.removeByKeys(deleteFileInfoIdList);
            e.onNext(needUpdateFileidList);
            e.onComplete();
        }).compose(RxSchedulersHelper.io()).subscribe(list -> {
            boxStore.runInTx(() -> {
                for (FileInfo fileInfo : list) {
                    File f = new File(fileInfo.getFilePath());
                    fileInfo.setFileName(f.getName());
                    fileInfo.setFilePath(f.getAbsolutePath());
                    fileInfo.setParentPath(f.getParent());
                    fileInfo.setExtension(FileUtils.getExtensionName(fileInfo.getFileName()));
                    fileInfo.setLastModifyTime(f.lastModified());
                    fileInfo.setFileSize(f.length());
                    fileInfo.setIsFile(f.isFile());
                    fileInfo.setFileType(getFileType(fileInfo.getExtension()));
                }
                fileInfoBox.put(list);
            });
        }, error -> {
        });
    }

    private void traverseChild(String parentPath, List<Long> delList, List<FileInfo> updateList) {
        List<FileInfo> childList = fileInfoBox.query().equal(FileInfo_.parentPath, parentPath).build().find();
        for (FileInfo childInfo : childList) {
            File file = new File(childInfo.getFilePath());
            if (!file.exists()) {
                delList.add(childInfo.getId());
            } else {
                if (childInfo.getLastModifyTime() != file.lastModified()) {//当前文件夹，时间不同，需要更新
                    updateList.add(childInfo);

                    if (!childInfo.getIsFile()) {
                        traverseChild(childInfo.getFilePath(), delList, updateList);
                    }
                }
            }
        }
    }
}
