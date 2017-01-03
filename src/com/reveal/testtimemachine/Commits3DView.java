package com.reveal.testtimemachine;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.geom.Rectangle2D;
import java.util.*;

public class Commits3DView extends JComponent implements ComponentListener
{
    ///////// ++ Constant ++ /////////
    ///////// -- Constant -- /////////

    ///////// ++ UI ++ /////////
    CustomEditorTextField mainEditorWindow;
    Point centerOfThisComponent;
    ///////// -- UI -- /////////

    String debuggingText = "";

    //////// ++ Timer and Timing
    Timer playing3DAnimationTimer;
    final int TICK_INTERVAL_MS = 50;
    boolean onChangingCommitProcess = false;
    ///////

    ///////// ++ UI: 3D Prespective Variables ++ /////////
    final float BASE_DEPTH = 2f; // Min:1.0
    final float LAYER_DISTANCE = 0.2f;
    final float LAYERS_DEPTH_ERROR_THRESHOLD = LAYER_DISTANCE/10;
    float maxVisibleDepth = 2f;
    final float MIN_VISIBLE_DEPTH = -LAYER_DISTANCE;
    final float MAX_VISIBLE_DEPTH_CHANGE_VALUE = 0.3f;
    final float EPSILON = 0.01f;
    /////
    int topLayerIndex=0, targetLayerIndex=0 /*if equals to topLayerIndex it means no animation is running*/;
    float topLayerOffset;
    Dimension topLayerDimention = new Dimension(0,0);
    Point topLayerCenterPos = new Point(0,0);
    int lastHighlightVirtualWindowIndex=-1;
    ///////// ++ UI
    final boolean COLORFUL = false;
    final int TOP_BAR_HEIGHT = 25;
    final int VIRTUAL_WINDOW_BORDER_TICKNESS = 1;
    ////////


    Project project;
    ArrayList<CommitWrapper> commitList = null;
    VirtualEditorWindow[] virtualEditorWindows = null;
    VirtualFile virtualFile;



    public Commits3DView( Project project, VirtualFile virtualFile, ArrayList<CommitWrapper> commitList)
    {
        super();

        this.project = project;
        this.virtualFile = virtualFile;
        this.commitList = commitList;

        this.setLayout(null);
        this.addComponentListener(this); // Check class definition as : ".. implements ComponentListener"
        if (CommonValues.IS_UI_IN_DEBUGGING_MODE)   this.setBackground(Color.ORANGE);
        this.setOpaque(true);

        setupUI_mainEditorWindow();
        setupUI_virtualWindows();
        initialVirtualWindowsVisualizations(); // initial 3D Variables

        componentResized(null); //to "updateTopIdealLayerBoundary()" and then "updateEverythingAfterComponentResize()"

        playing3DAnimationTimer = new Timer(TICK_INTERVAL_MS, new ActionListener(){
            public void actionPerformed(ActionEvent e)
            {
                tick(TICK_INTERVAL_MS/1000.f);
                repaint();
            }
        });
    }

    private void setupUI_mainEditorWindow()
    {
        //mainEditorWindow = new CustomEditorTextField(FileDocumentManager.getInstance().getDocument(virtualFile), project, FileTypeRegistry.getInstance().getFileTypeByExtension("java"),true,false);
        mainEditorWindow = new CustomEditorTextField("",project, FileTypeRegistry.getInstance().getFileTypeByExtension("java"));
        mainEditorWindow.setEnabled(true);
        mainEditorWindow.setOneLineMode(false);
        this.add(mainEditorWindow); // As there's no layout, we should set Bound it. we'll do this in "ComponentResized()" event
    }

    private void setupUI_virtualWindows()
    {
        virtualEditorWindows = new VirtualEditorWindow[commitList.size()];

        for (int i = 0; i< commitList.size() ; i++)
        {
            virtualEditorWindows[i] = new VirtualEditorWindow(i /*not cIndex*/, commitList.get(i));
        }
    }

    private void updateVirtualWindowsBoundaryAfterComponentResize()
    {
        int xCenter, yCenter, w, h;
        w = topLayerDimention.width;
        h = topLayerDimention.height;
        xCenter = topLayerCenterPos.x;
        yCenter = topLayerCenterPos.y;

        for (int i = 0; i< commitList.size() ; i++)
            virtualEditorWindows[i].setDefaultValues(xCenter, yCenter, w, h);
    }

    private void initialVirtualWindowsVisualizations()
    {
        topLayerOffset = 0;
        topLayerIndex = lastHighlightVirtualWindowIndex = 0;

        // Don't forget to call `updateVirtualWindowsBoundryAfterComponentResize()` before
        for (int i = 0; i< commitList.size() ; i++)
        {
            float d = i * LAYER_DISTANCE;
            virtualEditorWindows[i].updateDepth(d);
        }

        highlight(topLayerIndex);
    }

    public void increaseMaxVisibleDepth()
    {
        changeMaxVisibleDepth(MAX_VISIBLE_DEPTH_CHANGE_VALUE);
    }

    public void decreaseMaxVisibleDepth()
    {
        changeMaxVisibleDepth(-1*MAX_VISIBLE_DEPTH_CHANGE_VALUE);
    }

    private void changeMaxVisibleDepth(float delta)
    {
        if(maxVisibleDepth+delta<=LAYER_DISTANCE) return;
        maxVisibleDepth += delta;
        render();
    }

    private void highlight( int virtualWindowIndex)
    {
        virtualEditorWindows[lastHighlightVirtualWindowIndex].setHighlightBorder(false);

        virtualEditorWindows[virtualWindowIndex].setHighlightBorder(true);

        lastHighlightVirtualWindowIndex = virtualWindowIndex;
    }

    @Override
    public void componentResized(ComponentEvent e)
    {
        Dimension size = getSize();
        centerOfThisComponent = new Point(size.width/2, size.height/2);
        //////
        updateTopIdealLayerBoundary();
        //virtualEditorWindows[topLayerIndex].setHighlightBorder(); // Why here?
    }

    @Override
    public void componentMoved(ComponentEvent e) {}

    @Override
    public void componentShown(ComponentEvent e) {}

    @Override
    public void componentHidden(ComponentEvent e) {}

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);

        if(CommonValues.IS_UI_IN_DEBUGGING_MODE)
        {
            g.setColor(new Color(0,255,255));
            g.fillRect(0, 0,getSize().width,getSize().height);
            g.setColor(new Color(255,0,0));
            g.fillOval(getSize().width/2-10, getSize().height/2-10,20,20); //Show Center
        }

        if(virtualEditorWindows!=null)
        {
            for(int i = commitList.size()-1; i>=0; i--)
            {
                if(virtualEditorWindows[i].isVisible==false) continue;
                virtualEditorWindows[i].draw(g);

            }
        }


        //g.drawString(debuggingText,20,20);
    }

    private void tick(float dt_sec)
    {

        float targetDepth = virtualEditorWindows[targetLayerIndex].depth;
        float targetDepthAbs = Math.abs(virtualEditorWindows[targetLayerIndex].depth);
        int sign = (int) Math.signum(targetDepth);

        float speed_depthPerSec = 0;

        if(targetDepthAbs<=LAYERS_DEPTH_ERROR_THRESHOLD)
            speed_depthPerSec = targetDepth/dt_sec;
        else if(targetDepthAbs<4*LAYER_DISTANCE)
            speed_depthPerSec = 2*LAYER_DISTANCE*sign;
        else if(targetDepthAbs<5)
            speed_depthPerSec = 2*sign;
        else
            speed_depthPerSec = 5*sign;

        debuggingText = "> "+targetDepth+" > Speed: "+speed_depthPerSec;

        /*if(targetDepthAbs<LAYER_DISTANCE)
            speed_depthPerSec = 0.05f*sign;
        else
            speed_depthPerSec = 1*sign;*/

        float deltaDepth = dt_sec * speed_depthPerSec;


        int indexCorrespondingToLowestNonNegativeDepth=-1;
        float lowestNonNegativeDepth=Float.MAX_VALUE;

        for(int i=0; i<commitList.size(); i++)
        {
            float newDepth = virtualEditorWindows[i].depth - deltaDepth;

            virtualEditorWindows[i].updateDepth(newDepth);


            if(newDepth>=0 && newDepth<lowestNonNegativeDepth)
            {
                lowestNonNegativeDepth = newDepth;
                indexCorrespondingToLowestNonNegativeDepth = i;
            }
        }


        topLayerIndex = indexCorrespondingToLowestNonNegativeDepth;

        targetDepth = virtualEditorWindows[targetLayerIndex].depth;
        if(targetDepth>=0 && targetDepth<0.03)
        {
            // Assert topLayerIndex == targetLayerIndex;
            debuggingText = "<>";
            stopAnimation();
        }


        return;




        /*final int LAST_STEP_SPEED = 4;
        int sign = (int) Math.signum(topLayerIndex - targetLayerIndex);
        int diff = Math.abs(targetLayerIndex - topLayerIndex);
        if(targetLayerIndex == topLayerIndex)
            numberOfPassingLayersPerSec_forAnimation = -LAST_STEP_SPEED;
        else if(diff < 2)
            numberOfPassingLayersPerSec_forAnimation = LAST_STEP_SPEED*sign;
        else if(diff<6)
            numberOfPassingLayersPerSec_forAnimation = 6*sign;
        else
            numberOfPassingLayersPerSec_forAnimation = 9*sign;


        ////// TEMP ///////////////
        ////// TEMP ///////////////
        ////// TEMP ///////////////
        numberOfPassingLayersPerSec_forAnimation = sign*4;


        // TODO: maybe we overpass the target index commit
        topLayerOffset += numberOfPassingLayersPerSec_forAnimation * dt_sec * LAYER_DISTANCE;

        // When: numberOfPassingLayersPerSec_forAnimation is NEGATIVE
        // When: Moving direction FROM screen
        // currentCommitIndex = 0 ===> targetCommitIndex = 10
        if(topLayerOffset < 0)
        {
            // TODO: Still the result of sum may be negative
            topLayerOffset = (topLayerOffset+LAYER_DISTANCE)%LAYER_DISTANCE; // Here we may need to pass two layers
            topLayerIndex++;                                                // Here we may need to pass two layers
            //assert topLayerIndex >= commitList.size(); // TODO
            //if(topLayerIndex >= commitList.size())
            //    topLayerIndex=0;
        }

        // When: numberOfPassingLayersPerSec_forAnimation is POSITIVE
        // When: Moving direction INTO screen
        if(topLayerOffset > LAYER_DISTANCE)
        {
            topLayerOffset = topLayerOffset%LAYER_DISTANCE; // Here we may need to pass two layers
            topLayerIndex--;                                // Here we may need to pass two layers
            //assert topLayerIndex < 0; // TODO
            //if(topLayerIndex < 0)
            //    topLayerIndex=commitList.size()-1;
        }

        for(int i=0; i<commitList.size(); i++)
        {
            virtualEditorWindows[i].updateDepth((i-topLayerIndex)*LAYER_DISTANCE + topLayerOffset);
            if(virtualEditorWindows[i].depth<0 || virtualEditorWindows[i].depth>maxVisibleDepth)
                virtualEditorWindows[i].isVisible=false;
            else
                virtualEditorWindows[i].isVisible=true;

            //int layerIndex_ith_after_topLayer = (topLayerIndex+i)%commitList.size();

            //if(layerIndex_ith_after_topLayer < topLayerIndex
                    //|| layerIndex_ith_after_topLayer>topLayerIndex + maxVisibleDepth)
                //virtualEditorWindows[layerIndex_ith_after_topLayer].isVisible = false;
            //else
                //virtualEditorWindows[layerIndex_ith_after_topLayer].isVisible = true;

            //virtualEditorWindows[layerIndex_ith_after_topLayer].updateDepth(i*LAYER_DISTANCE + topLayerOffset);
        }

        float d = virtualEditorWindows[targetLayerIndex].depth - 0;
        float abs = Math.abs(d);
        if(abs<0.1)
        {
            stopAnimation();
            virtualEditorWindows[topLayerIndex].setHighlightBorder();
        }*/
    }

    public void showCommit(int newCommitIndex, boolean withAnimation) // TODO: without animation
    {
        if(withAnimation==false)
        {
            // TODO
            //loadMainEditorWindowContent();
            //virtualEditorWindows[topLayerIndex].setHighlightBorder();
            // TODO: Arrange VirtualEditorWindows
        }
        else
        {
            if( targetLayerIndex==newCommitIndex) //TODO : is cIndex ?
                return;

            playAnimation(newCommitIndex);
            mainEditorWindow.setVisible(false);
        }
    }

    private void playAnimation(int newCommitIndex)
    {
        onChangingCommitProcess = true;
        this.targetLayerIndex = newCommitIndex;
        highlight(targetLayerIndex);
        if(!playing3DAnimationTimer.isRunning())
            playing3DAnimationTimer.start();
    }

    private String getStringFromCommits(int commitIndex)
    {
        String content= commitList.get(commitIndex).getFileContent();
        return content;
    }

    public void stopAnimation()
    {
        loadMainEditorWindowContent();

        playing3DAnimationTimer.stop();
        onChangingCommitProcess = false;
    }

    private void render()
    {
        for(int i=0; i<commitList.size(); i++)
        {
            float d = virtualEditorWindows[i].depth;
            virtualEditorWindows[i].updateDepth(d);

        }
        repaint();
    }

    private void loadMainEditorWindowContent()
    {
        String content = getStringFromCommits(topLayerIndex);

        ApplicationManager.getApplication().invokeLater(new Runnable()
        {
            @Override
            public void run()
            {
                mainEditorWindow.setText(content);
            }
        });

        updateMainEditorWindowBoundaryAfterComponentResize();
        mainEditorWindow.setCaretPosition(0); // TODO: Doesn't Work
        mainEditorWindow.setVisible(true);

    }

    private void updateMainEditorWindowBoundaryAfterComponentResize()
    {
        int x,y,w,h;
        w = virtualEditorWindows[topLayerIndex].drawingRect.width-2*VIRTUAL_WINDOW_BORDER_TICKNESS;
        h = virtualEditorWindows[topLayerIndex].drawingRect.height-TOP_BAR_HEIGHT;
        x = virtualEditorWindows[topLayerIndex].drawingRect.x-w/2+VIRTUAL_WINDOW_BORDER_TICKNESS;
        y = virtualEditorWindows[topLayerIndex].drawingRect.y-h/2+TOP_BAR_HEIGHT/2;
        mainEditorWindow.setBounds(x,y,w,h);
    }

    private void updateTopIdealLayerBoundary()
    {
        final int FREE_SPACE_VERTICAL = 100, FREE_SPACE_HORIZONTAL = 60;
        ////
        //topLayerDimention = new Dimension(  getSize().width - FREE_SPACE_HORIZONTAL /*Almost Fill Width*/,
        //                                    2*getSize().height/3 /*2/3 of whole vertical*/);
        topLayerDimention = new Dimension(  getSize().width/2, 2*getSize().height/3 /*2/3 of whole vertical*/);
        topLayerCenterPos = new Point(centerOfThisComponent.x, 2*getSize().height/3 /*Fit from bottom*/);
        ////
        updateEverythingAfterComponentResize();
    }

    private void updateEverythingAfterComponentResize()
    {
        updateVirtualWindowsBoundaryAfterComponentResize();
        updateMainEditorWindowBoundaryAfterComponentResize();
    }

    protected class VirtualEditorWindow
    {
        final float Y_OFFSET_FACTOR = 250;
        ////////
        int index=-1;
        CommitWrapper commitWrapper = null;

        boolean isVisible=true;
        float depth;
        int alpha=255;
        Color DEFAULT_BORDER_COLOR = Color.GRAY;
        Color DEFAULT_TOP_BAR_COLOR = Color.GRAY;

        Color myColor=Color.WHITE, myBorderColor=DEFAULT_BORDER_COLOR, myTopBarColor=DEFAULT_TOP_BAR_COLOR;

        int xCenterDefault, yCenterDefault, wDefault, hDefault;
        Rectangle drawingRect = new Rectangle(0, 0, 0, 0);
        ////////

        public VirtualEditorWindow(int index, CommitWrapper commitWrapper)
        {
            this.index = index;
            this.commitWrapper = commitWrapper;


            if(COLORFUL || CommonValues.IS_UI_IN_DEBUGGING_MODE)
            {
                Random rand = new Random();
                float r = rand.nextFloat();
                float g = rand.nextFloat();
                float b = rand.nextFloat();
                this.myColor = new Color(r,g,b);
            }
        }

        // this function should be called on each size change
        public void setDefaultValues(int xCenterDefault, int yCenterDefault, int wDefault, int hDefault)
        {
            if(wDefault<=0 || hDefault<=0) return; //Window is not intialized corerctly yet

            this.xCenterDefault = xCenterDefault;
            this.yCenterDefault = yCenterDefault;
            this.wDefault = (int) (wDefault*BASE_DEPTH);
            this.hDefault = (int) (hDefault*BASE_DEPTH);

            // Now update Boundary according to current depth and new DefaultValues
            updateDepth(depth);

        }

        public void setAlpha(int newAlpha)
        {
            if(newAlpha>255)
                newAlpha=255;
            if(newAlpha<0)
                newAlpha=0;
            alpha = newAlpha;
            myBorderColor = new Color(myBorderColor.getRed(), myBorderColor.getGreen(), myBorderColor.getBlue(), alpha);
            myTopBarColor = new Color(myTopBarColor.getRed(), myTopBarColor.getGreen(), myTopBarColor.getBlue(), alpha);
            myColor = new Color(myColor.getRed(), myColor.getGreen(), myColor.getBlue(), alpha);
        }

        public void updateDepth(float depth)
        {
            this.depth = depth;

            if(depth<MIN_VISIBLE_DEPTH || depth> maxVisibleDepth)
                isVisible=false;
            else
                isVisible=true;

            if(isVisible)
                doRenderCalculation();
        }

        public void doRenderCalculation()
        {
            float renderingDepth = depth + BASE_DEPTH;
            Rectangle rect = new Rectangle(0, 0, 0, 0);

            //////////////// Alpha
            int newAlpha;
            if(depth>0)
                newAlpha = (int)(255*(1-depth/ maxVisibleDepth));
            else
                // By adding LAYERS_DEPTH_ERROR_THRESHOLD we make the layer invisible(alpha=0) before getting depth=-LAYER_DISTANCE
                // It's needed because sometimes layers movement stops while the new top layer is 0+e (not 0) and so the layer
                // which was supposed to go out (go to depth -LAYER_DISTANCE) get stuck at depth = -LAYERDISTANCE+e.
                newAlpha = (int)(255*(1-depth/(MIN_VISIBLE_DEPTH+LAYERS_DEPTH_ERROR_THRESHOLD+EPSILON)));
            setAlpha(newAlpha);


            //////////////// Size
            rect.width = render3DTo2D(wDefault, renderingDepth);
            rect.height = render3DTo2D(hDefault, renderingDepth);
            Point p = render3DTo2D(xCenterDefault, yCenterDefault, renderingDepth);
            rect.x = p.x;
            rect.y = p.y;

            drawingRect = rect;
        }

        private int render3DTo2D(int dis, float z)
        {
            return ((int) (dis / z));
        }

        private Point render3DTo2D(int x, int y, float z)
        {
            Point p = new Point();
            p.x = x;
            p.y = y - (int) (Math.log(z - BASE_DEPTH + Math.exp(0)) * Y_OFFSET_FACTOR);
            return p;
        }

        public void setHighlightBorder(boolean newStatus)
        {
            if(newStatus==true)
                myBorderColor = Color.RED;
            else
                myBorderColor = DEFAULT_BORDER_COLOR;

            setAlpha(alpha); //Apply current alpha to above solid colors
        }

        public void setHighlightTopBar(boolean newStatus)
        {
            if(newStatus==true)
                myTopBarColor = Color.GREEN;
            else
                myTopBarColor = DEFAULT_TOP_BAR_COLOR;

            setAlpha(alpha); //Apply current alpha to above solid colors
        }

        public void draw(Graphics g)
        {
            Graphics2D g2d = (Graphics2D) g;
            if(this.isVisible!=true) return;

            int x,y,w,h; //TODO: Create drawingRect not centered and make a function for below
            w = this.drawingRect.width;
            h = this.drawingRect.height;
            x = this.drawingRect.x - w/2;
            y = this.drawingRect.y - h/2;

            /// Rect
            g.setColor( this.myColor);
            g.fillRect(x, y, w, h);

            /// Border
            g2d.setStroke(new BasicStroke(2));
            g2d.setColor( this.myBorderColor);
            g2d.drawRect(x, y, w, h);

            /// TopBar
            g.setColor( this.myTopBarColor);
            g.fillRect(x, y+VIRTUAL_WINDOW_BORDER_TICKNESS, w, TOP_BAR_HEIGHT);

            /// Name
            g.setColor(new Color(0,0,0,alpha));

            String text="";
            Graphics g2 = g.create();
            Rectangle2D rectangleToDrawIn = new Rectangle2D.Double(x,y,w,TOP_BAR_HEIGHT);
            g2.setClip(rectangleToDrawIn);
            if(index==topLayerIndex)
            {
                g2.setFont(new Font("Courier", Font.BOLD, 10));
                text = getTopBarMessage();
                //text = "I: "+index+"Depth = "+ Float.toString(depth)+ "FontSize: --  "+ "Alpha: "+alpha;
                DrawingHelper.drawStringCenter(g2, text, x+w/2, y+8);
                String path = virtualFile.getPath();
                path = fit(path, 70, g2);
                text = new String(path);
                DrawingHelper.drawStringCenter(g2, text, x+w/2, y+18);
            }
            else
            {
                float fontSize = 20.f/(BASE_DEPTH+depth);
                g2.setFont(new Font("Courier", Font.BOLD, (int)fontSize));
                text = getTopBarMessage();
                //text = "I: "+index+"Depth = "+ Float.toString(depth)+ "FontSize: "+fontSize + "Alpha: "+alpha;
                DrawingHelper.drawStringCenter(g2, text, x+w/2, y+15);
            }
        }

        private String getTopBarMessage()
        {
            String text;
            if(commitWrapper.isFake())
                text = new String(commitWrapper.getHash());
            else
                text = new String("Commit "+commitWrapper.getHash());
            return text;
        }

        private String fit(String s, int maxCharacterCount, Graphics g)
        {
            String result = s;
            int extraCharacterCount = result.length() - maxCharacterCount;
            int cropFrom = result.indexOf('/', extraCharacterCount);
            result = result.substring(cropFrom);
            result = "..."+result;
            return result;
        }


    } // End of VirtualEditorWindow class

    class CustomEditorTextField extends EditorTextField
    {
        // >>>>>>>> Scroll for EditorTextField
        // https://intellij-support.jetbrains.com/hc/en-us/community/posts/206759275-EditorTextField-and-surrounding-JBScrollPane

        public CustomEditorTextField(Document document, Project project, FileType fileType, boolean isViewer, boolean oneLineMode)
        {
            super(document,project,fileType,isViewer,oneLineMode);
        }

        public CustomEditorTextField(@NotNull String text, Project project, FileType fileType) {
            this(EditorFactory.getInstance().createDocument(text), project, fileType, true, true);
        }

        @Override
        protected EditorEx createEditor()
        {
            EditorEx editor = super.createEditor();
            editor.setVerticalScrollbarVisible(true);
            editor.setHorizontalScrollbarVisible(true);
            addLineNumberToEditor(editor);
            return editor;
        }

        private void addLineNumberToEditor(EditorEx editor)
        {
            EditorSettings settings = editor.getSettings();
            settings.setLineNumbersShown(true);
            editor.reinitSettings();
        }

    }

} // End of Commits3DView class
