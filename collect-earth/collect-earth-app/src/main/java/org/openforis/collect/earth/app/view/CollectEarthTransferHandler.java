package org.openforis.collect.earth.app.view;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.TransferHandler;

import org.openforis.collect.earth.app.CollectEarthUtils;
import org.openforis.collect.earth.app.desktop.EarthApp;
import org.openforis.collect.earth.app.service.LocalPropertiesService;
import org.openforis.collect.earth.app.service.LocalPropertiesService.EarthProperty;
import org.openforis.collect.earth.sampler.utils.KmlGenerationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CollectEarthTransferHandler extends TransferHandler {
	
	
	private CollectEarthWindow collectEarthWindow;
	private LocalPropertiesService localPropertiesService;
	
	public CollectEarthTransferHandler(CollectEarthWindow collectEarthWindow, LocalPropertiesService localPropertiesService) {
		super();
		this.collectEarthWindow = collectEarthWindow;
		this.localPropertiesService = localPropertiesService;
	}


	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	Logger logger = LoggerFactory.getLogger( CollectEarthTransferHandler.class);
	
	/* 
	 * Can Only import an object if it represents a SINGLE file
	 * (non-Javadoc)
	 * @see javax.swing.TransferHandler#canImport(javax.swing.TransferHandler.TransferSupport)
	 */
	@Override
	public boolean canImport(TransferSupport support) {
		
		if( support.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ){
			 Transferable t = support.getTransferable();
			 List<File> data;
		     try {
		            data = (List<File>)t.getTransferData(DataFlavor.javaFileListFlavor);
		            
		            if( data.size() == 1){
		            	return isFileExtensionValid( data.get(0) );
		            }else {
		            	return false;
		            }
		            
		     }catch(java.awt.dnd.InvalidDnDOperationException unknownException) {
		    	 logger.error("EWhy do I get this error?t", unknownException);
	    	 	return true; 
		     }catch (Exception e) {
		    	 logger.error("Error on the drop support assessment", e);
		    	 return false; 
		    } 
		}else{
			return false;
		}
			
	}
	

    private String getFileExtension(File file) {
		String fileName = file.getName();
		int lastIndexOf = fileName.lastIndexOf('.' );
		String fileExtenxion = null;
		if( lastIndexOf >0 ){
			fileExtenxion = fileName.substring( lastIndexOf + 1 );
		}
		return fileExtenxion;
	}


    private boolean isFileExtensionValid(File file ){
    	String fileExtension = getFileExtension(file);
    	return fileExtension!=null && ( DataFormat.COLLECT_COORDS.checkFileExtensionMatches(fileExtension) || DataFormat.PROJECT_DEFINITION_FILE.checkFileExtensionMatches(fileExtension) );
    }
    
	/* 
     * We support both copy and move actions.
     * (non-Javadoc)
     * @see javax.swing.TransferHandler#getSourceActions(javax.swing.JComponent)
     */
    public int getSourceActions(JComponent c) {
        return COPY_OR_MOVE;
    }
    
    
    /* 
     * Perform the actual import.  This demo only supports drag and drop.
     * (non-Javadoc)
     * @see javax.swing.TransferHandler#importData(javax.swing.TransferHandler.TransferSupport)
     */
    public boolean importData(TransferSupport info) {
        if (!info.isDrop()) {
            return false;
        }

        // Get the file that is being dropped.
        Transferable t = info.getTransferable();
        List<File> data;
        try {
            data = (List<File>)t.getTransferData(DataFlavor.javaFileListFlavor);
            if( data.size()!=1){
            	throw new IllegalArgumentException("The drop action supports only single file drops!");
            }
            
            File fileToImport = data.get(0);
            
            if( !isFileExtensionValid(fileToImport)){
            	throw new IllegalArgumentException("The drop action supports only files of type!" + DataFormat.COLLECT_COORDS + " or " + DataFormat.PROJECT_DEFINITION_FILE);
            }
            
            String fileExtension = getFileExtension(fileToImport);
            
            // If it is a CEP file then import
            if( DataFormat.PROJECT_DEFINITION_FILE.checkFileExtensionMatches(fileExtension)){
            	EarthApp.openProjectFileInRunningCollectEarth( fileToImport.getAbsolutePath() );
            }else if( DataFormat.COLLECT_COORDS.checkFileExtensionMatches(fileExtension)){
            	
            	// Check if the CSV file can be loaded in the survey!!!
            	
            	try{
            		if( CollectEarthUtils.validateCsvColumns( fileToImport ) ){
            			
            			localPropertiesService.setValue( EarthProperty.SAMPLE_FILE, fileToImport.getAbsolutePath() );
                    	EarthApp.executeKmlLoadAsynchronously( collectEarthWindow.getFrame() );
            		}
            		
            	}catch( KmlGenerationException kmlGenerationException ){
            		logger.error( "Problem loading CSV file dropped into the window" , kmlGenerationException );
            		EarthApp.showMessage(" Problem loading CSV file" + kmlGenerationException.getCause() );
            	}
            	
            	
            }else{
            	throw new IllegalArgumentException("Unknown file extension!!");
            }
            
        } 
        catch (Exception e) {
        	logger.error("Error on the drop action", e);
        	return false; 
        }
                                
       
        
        
        return true;
    }

    
}
