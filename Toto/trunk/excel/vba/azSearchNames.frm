VERSION 5.00
Begin {C62A69F0-16DC-11CE-9E98-00AA00574A4F} azSearchNames 
   Caption         =   "Select name"
   ClientHeight    =   8430
   ClientLeft      =   45
   ClientTop       =   -945
   ClientWidth     =   10350
   OleObjectBlob   =   "azSearchNames.frx":0000
   StartUpPosition =   1  'CenterOwner
End
Attribute VB_Name = "azSearchNames"
Attribute VB_GlobalNameSpace = False
Attribute VB_Creatable = False
Attribute VB_PredeclaredId = True
Attribute VB_Exposed = False

'Build 025
'***************************************************************************
'
' Authors:  JKP Application Development Services, info@jkp-ads.com, http://www.jkp-ads.com
'           Peter Thornton, pmbthornton@gmail.com
'
' (c)2013, all rights reserved to the authors
'
' You are free to use and adapt the code in these modules for
' your own purposes and to distribute as part of your overall project.
' However all headers and copyright notices should remain intact
'
' You may not publish the code in these modules, for example on a web site,
' without the explicit consent of the authors
'***************************************************************************

Option Explicit

'##########Treeview Code##########
'Add this to your form's declaration section
Private WithEvents mcTree As clsTreeView
Attribute mcTree.VB_VarHelpID = -1
Private mbExit As Boolean    ' to exit a SpinButton event
'/##########Treeview Code##########
Private AppName As String
Private mlDemoNo As Integer
Private azNameCount As Integer
Private ListNameChosen As String


#If Mac Then
    Const mcPtPixel As Long = 1
#Else
    Const mcPtPixel As Single = 0.75
#End If

Private Sub lblLink_Click()
    ActiveWorkbook.FollowHyperlink "http://www.jkp-ads.com"
End Sub

Private Sub azSearchName_Change()

End Sub

Private Sub frTreeControl_MouseMove(ByVal Button As Integer, ByVal Shift As Integer, ByVal X As Single, ByVal Y As Single)

End Sub

Private Sub SubmitButton_Click()

   If ListNameChosen = "" Then
      ListNameChosen = mcTree.activeNode.caption
   End If
   azNameChosen = ListNameChosen
   azSearchNames.Hide
End Sub
Public Sub UserForm_Initialize()
'see the Compile constant DebugMode in tools, vbaproject properties
'DebugMode=1 will enable the #If to Stop in Error handlers
    
    Dim submenu As New popup
    
    ListNameChosen = ""
    Set az_RgtClkMenu = submenu.CreateSubMenu(False)
    
    
    ' Hide the Image container
    Me.frmImageBox.Visible = False
    Me.frmImageBox.Enabled = False
    Me.Width = 332
    mbExit = True
    
    'Me.labIndent.Tag = Me.labIndent.Caption
    'Me.labNodeHeight.Tag = Me.labNodeHeight.Caption
    'Me.labFont.Tag = Me.labFont.Caption
    'Me.txtChildren.value = 15: mlCntChildren = 15
    'Me.SpinButton1.value = 20
    'Me.SpinButton2.value = 16
    If Me.frTreeControl.Font.Size < 6 Then
         Me.frTreeControl.Font.Size = 4
    End If
    'Me.SpinButton3.value = Me.frTreeControl.Font.Size
    mbExit = False
    'lblBuild.Caption = "Build Number: " & GCSBUILD & " "
    
    #If DebugMode = 1 Then
        gFormInit = gFormInit + 1
    #End If
    
    #If Mac Then
        Dim objCtl As MSForms.Control
        
        With Me
            .Font.Size = 10
            .Width = .Width * 4 / 3
            .Height = .Height * 4 / 3
            '.BackColor = .labInfo.BackColor
        End With

        For Each objCtl In Me.Controls
            With objCtl
                .Left = Int(.Left * 4 / 3)
                .Top = Int(.Top * 4 / 3)
                .Width = Int(.Width * 4 / 3)
                .Height = Int(.Height * 4 / 3)
                Select Case TypeName(objCtl)
                Case "Image", "SpinButton"
                Case "TextBox", "Frame"
                    .Font.Size = 10
                Case Else
                    .Font.Size = 10
                End Select
            End With
        Next
    #End If
    azShowWithoutIcons
End Sub

Private Sub UserForm_Terminate()
  
    #If DebugMode = 1 Then
        gFormTerm = gFormTerm + 1
        ClassCounts
    #End If
End Sub

Private Sub azShowWithoutIcons()

    'cmdTestSort.Visible = Me.cbxTestSort.value
    'Me.cbxCheckBoxes.Caption = "ChkBox TriState"
    azInitializeTree
    'Me.SpinButton3.Enabled = False
    mlDemoNo = 1
    'Me.SpinButton3.Enabled = False
    If Not mcTree Is Nothing Then
        mbExit = True
        'SpinButton1 = mcTree.Indentation / mcPtPixel
        'SpinButton2 = mcTree.NodeHeight / mcPtPixel
        UpdateInfoLabel
        mbExit = False
       ' Me.frTreeControl.SetFocus
    End If
End Sub

Private Sub cmdStop_Click()
    If Not mcTree Is Nothing Then
        mcTree.NodesClear
        Set mcTree = Nothing
    End If
    mlDemoNo = 0
    'Me.labInfo = ""
    Me.caption = AppName
    cmdTestSort.Visible = False
    Me.SpinButton3.Enabled = True
    Me.cbxCheckBoxes.caption = "Checkboxes"
End Sub

'########## Treeview Demo ##########

Private Sub UserForm_QueryClose(Cancel As Integer, CloseMode As Integer)
'Make sure all objects are destroyed
    If Not mcTree Is Nothing Then
        mcTree.TerminateTree
    End If
    Set mcTree = Nothing

End Sub

Private Sub azShowChildren(cParent As clsNode, keyname As Object)
   On Error GoTo endsub
   If keyname("children").Count = 0 Then
      Exit Sub
   End If
   cParent.Expanded = False
   Dim azChild As Object
   For Each azChild In keyname("children")
     Dim cChild As clsNode
     Set cChild = cParent.AddChild(azNameCount & "", azChild("name"))
     azNameCount = azNameCount + 1
     cChild.AzquoName = azChild
      Call azShowChildren(cChild, azChild)
   Next
   
endsub:
        Debug.Print Err.Source, Err.Description

End Sub



Private Sub azInitializeTree()
'-------------------------------------------------------------------------
' Procedure : Initialize
' Company   : JKP Application Development Services (c)
' Author    : Jan Karel Pieterse (www.jkp-ads.com)
' Created   : 15-01-2013
' Purpose   : Initializes the userform,
'             adds the VBA treeview to the container frame on the userform
'             and populates the treeview.
'-------------------------------------------------------------------------
    Dim cRoot As clsNode
    Dim cNode As clsNode
    Dim cExtraNode As clsNode
    Dim i As Long
    Dim k As Long
    Dim lib As New JSONLib
    Dim cbxShowExpanders As Boolean
    Dim cbxCheckBoxes As Boolean
    Set mcTree = New clsTreeView

    On Error GoTo errH
    
    'FIXED PARAMETERS
    
    cbxShowExpanders = True
    cbxCheckBoxes = False
    With mcTree
        'The VBA treeview needs a container Frame control on the userform.
        'Just draw a frame on your form where the treeview should appear
        'Make sure the frame has no border and no caption
        'Set it to show both scroll bars. (Keepscrollbarsvisible to both)
        'Set it to the special effect "Sunken"
        'Note that node label formats such as back/fore colors and font adopt
        'the frame container's properties, so change if/as required.
        '(you can copy the frame named frTreeControl from this userform )
        
        'Then pass this frame to the TreeControl of the treeview class

        Set .TreeControl = Me.frTreeControl

        Call .NodesClear

'        .AppName = Me.AppName
        
        'Set some characteristics of the root of the tree,
        'which for this demo we pick up from checkbox and spinner controls on the form
        .CheckBoxes(bTriState:=cbxCheckBoxes) = cbxCheckBoxes
        .RootButton = True
        '.LabelEdit(bAutoSort:=True) = IIf(cbxAllowEditing.Value, 0, 1)  'default 0, can be edited (like LabelEditConstants tvwAutomatic/tvwManual)
        ' new in v022, EnableLabelEdit added as alternative to LabelEdit
        .EnableLabelEdit(bAutoSort:=True) = False
        .FullWidth = True
        .Indentation = 15
        .NodeHeight = 12
        .ShowLines = True
        .ShowExpanders = True
        
        
        
        If cbxShowExpanders Then
            ' Win7 style arrow icons, try "Win7Plus1" & "Win7Plus2" for preference
            Call .ExpanderImage(Me.frmImageBox.Controls("Win7Minus").Picture, _
                                Me.frmImageBox.Controls("Win7Plus2").Picture)
        End If
        If cbxCheckBoxes Then
            Call .CheckboxImage(Me.frmImageBox.Controls("CheckboxFalse").Picture, _
                                Me.frmImageBox.Controls("CheckboxTrue").Picture, _
                                Me.frmImageBox.Controls("CheckboxNull").Picture)
        End If
        
        Dim jsonNames As Object
        Set jsonNames = lib.parse(azResponse)
        Dim keyname As Object
        azNameCount = 1
        For Each keyname In jsonNames("names")
         ' add a Root node and make it bold
          Set cRoot = .AddRoot(sKey:=azNameCount & "", vCaption:=keyname("name"))
          azNameCount = azNameCount + 1
          cRoot.Bold = True
          cRoot.ControlTipText = "Tip for Root Node" & k & ". Context tips can also be added to all nodes"
          cRoot.AzquoName = keyname
          Call azShowChildren(cRoot, keyname)
        Next
        
        'create the node controls and display the tree
        .Refresh ' note .PopulateTree is deprecated and replaced with Refresh

    End With

    Exit Sub
    
errH:
    #If DebugMode = 1 Then
        Debug.Print Err.Source, Err.Description
        Stop
        Resume
    #End If

    Debug.Print Err.Source, Err.Description

    If Not mcTree Is Nothing Then
        mcTree.NodesClear
    End If

End Sub



'/########## Treeview Demo ##########


'########### Treeview container frame events ################

' Enter/Exit events are not trapped with 'WithEvents' in the treeclass
' so if needed they can be trapped in the form. (These will toggle the active node's highlight states)

Private Sub frTreeControl_Enter()
    If Not mcTree Is Nothing Then
        mcTree.EnterExit False
    End If
End Sub

Private Sub frTreeControl_Exit(ByVal Cancel As MSForms.ReturnBoolean)
    If Not mcTree Is Nothing Then
        mcTree.EnterExit True
    End If
End Sub

'/########### Treeview container frame events ################


'########## Treeview Events, raised in clsTreeView ##########

'This gets fired after a node has been edited
Private Sub mcTree_AfterLabelEdit(Cancel As Boolean, NewString As String, cNode As clsNode)

' Validate user's manually edited node here

 
End Sub

'This gets fired when a node is clicked
Private Sub mcTree_Click(cNode As clsNode)
    Dim s As String
    With cNode
        s = .caption & IIf(mcTree.CheckBoxes, "   Checked:" & .Checked, "") & vbNewLine & _
            "Key:  " & .Key & vbNewLine & _
            "Index:  " & .Index & "    VisIndex:  " & .VisIndex & "    Level:  " & .Level

    End With
    ListNameChosen = cNode.caption
    'Me.labInfo.caption = s
End Sub
Public Sub mcCut()
   'emulate Control-X
    Dim submenu As New popup
    Set az_RgtClkMenu = submenu.CreateSubMenu(True)

    Set mcTree.MoveCopyNode(True) = mcTree.activeNode
   
End Sub
Public Sub mcCopy()
    Dim submenu As New popup
    Set az_RgtClkMenu = submenu.CreateSubMenu(True)

    Set mcTree.MoveCopyNode(False) = mcTree.activeNode
   
End Sub
Public Sub mcEdit()

   Dim cNode As clsNode
   
   
   Set cNode = mcTree.activeNode
   Set azName = cNode.AzquoName
   If Not azName Is Nothing Then
      Set NameEdit.eNode = cNode
      NameEdit.NameEdit_Initialize
      
      NameEdit.azNameValue.value = azName("name")
      
      NameEdit.DataItems = azName("mydataitems")
   
      Dim att As Variant
      For Each att In azName("attributes")
          Dim AttName As String
          Dim AttValue As String
          AttName = att
          AttValue = azName("attributes")(AttName)
          If (AttName <> "DEFAULT_DISPLAY_NAME") Then
             Call NameEdit.AddAttribute(AttName, AttValue)
          End If
     Next
   End If
   Call NameEdit.AddAttribute("", "")
   NameEdit.Show
   ListNameChosen = cNode.caption
   

End Sub

Public Sub mcPaste()
    Dim submenu As New popup
    Dim pos As Integer
    Dim cParent As clsNode
    Dim cTmp As clsNode
    Dim cNode As clsNode
    Dim bMove As Boolean
    Dim origNode As clsNode
    Dim azReply As String
    
    Set cNode = mcTree.activeNode
    Set az_RgtClkMenu = submenu.CreateSubMenu(False)
    Set cParent = cNode.ParentNode
    Set origNode = mcTree.MoveCopyNode(bMove)
    If origNode.ParentNode Is cParent Then
      'don't try to put the name twice in the same set
      bMove = True
    End If
    pos = 0
    For Each cTmp In cParent.ChildNodes
        pos = pos + 1
        If cTmp Is cNode Then
            Exit For
        End If
    Next
    azReply = TellAzquo(origNode, cParent, pos)
    mcTree.Copy origNode, cParent, vBefore:=pos, bShowError:=True
    
    If bMove Then
     If Not origNode.ParentNode Is cParent Then
         azReply = TellAzquo(origNode, origNode.ParentNode, 0)
      End If
      mcTree.NodeRemove origNode
    End If
    mcTree.Refresh

End Sub

'This gets fired when a key is pressed down
Private Sub mcTree_KeyDown(cNode As clsNode, ByVal KeyCode As MSForms.ReturnInteger, ByVal Shift As Integer)
' PT de
    Dim bMove As Boolean
    Dim sMsg As String
    Dim cSource As clsNode
    
    Select Case KeyCode
    Case vbKeyUp, vbKeyDown, vbKeyLeft, vbKeyRight, _
         48 To 57, 96 To 105, vbKeyF2, 20, 93, _
         vbKeyPageUp, vbKeyPageDown, vbKeyHome, vbKeyEnd
        ' these keys are already trapped in clsTreeView for navigation, expand/collapse, edit mode

    Case vbKeyC, vbKeyC + 48
        If Shift = 2 Then    ' Ctrl-X move
            ' code here to validate if user can copy this node
            Set mcTree.MoveCopyNode(False) = mcTree.activeNode
        End If

    Case vbKeyX, vbKeyX + 48
        If Shift = 2 Then    ' Ctrl-X move
            ' code here to validate if user can move this node
            Set mcTree.MoveCopyNode(True) = mcTree.activeNode
        End If

    Case vbKeyV, vbKeyV + 48
        If Shift = 2 Then    ' Ctrl-V paste
            Set cSource = mcTree.MoveCopyNode(bMove)
            If Not cSource Is Nothing Then
                ' code to validate if the stored 'MoveCopyNode' can be Moved or Copied to the selected node
                If bMove Then
                    mcTree.Move cSource, mcTree.activeNode, bShowError:=True
                Else
                    mcTree.Copy cSource, mcTree.activeNode, bShowError:=True
                End If

                mcTree.activeNode.Sort    ' assume user wants move/copy to locate as sorted
                mcTree.activeNode.Expanded = True    ' assume user wants to see the moved/copied node if behind a collapsed node
                mcTree.Refresh
            End If
        End If
    Case vbKeyDelete
        sMsg = "Are you sure you want to delete node ''" & cNode.caption & "'' and all it's child-nodes?" & vbCr & _
                        vbCr & "(press Ctrl-break now and click Debug to see this event code)"
        If MsgBox(sMsg, vbOKCancel, AppName) = vbOK Then
            Dim azReply As String
            azReply = TellAzquo(cNode, cNode.ParentNode, 0)
            mcTree.NodeRemove cNode
            mcTree.Refresh
        End If
    End Select
End Sub

Private Function TellAzquo(cNode As clsNode, cParent As clsNode, newPosition As Integer)

             Dim params As String
            params = """operation"":""edit"",""id"":" & cNode.AzquoName("id")
             If newPosition > 0 Then
               params = params & ",""newParent"":" & cParent.AzquoName("id") & ",""newPosition"":" & newPosition
            Else
               params = params & ",""oldParent"":" & cParent.AzquoName("id")

            End If
            TellAzquo = AzquoPost("Name", params)
            
            



End Function
'This gets fired when a node is checked
Private Sub mcTree_NodeCheck(cNode As clsNode)
     ListNameChosen = cNode.caption
    
End Sub

'/########## Treeview Events, raised in clsTreeView ##########


'########## Set some Treeview properties with controls on the demo form ##########

Private Sub txtchildren_AfterUpdate()
    On Error Resume Next
    mlCntChildren = CLng(txtChildren.value)
    If mlCntChildren > 5000 Then
        MsgBox mlCntChildren & " is a lot, press Ctrl-Break to abort loading the demo", , AppName
    End If
    txtChildren = mlCntChildren
End Sub


Private Sub cbxCheckBoxes_Click()
    If mbExit Then Exit Sub
    On Error GoTo locErr
    If cbxCheckboxIcons Then
        cbxCheckboxIcons = False
    ElseIf Not mcTree Is Nothing Then
        If mlDemoNo = 1 Then
            ' demo checkboxes with triState
            mcTree.CheckBoxes(bTriState:=True) = cbxCheckBoxes.value
        Else
            mcTree.CheckBoxes = cbxCheckBoxes.value
        End If
    End If
    Exit Sub
locErr:
    MsgBox Err.Description, , Err.Source
End Sub

Private Sub cbxCheckboxIcons_Click()
'PT
    If cbxCheckboxIcons And Not cbxCheckBoxes Then
        mbExit = True
        cbxCheckBoxes = True
        mbExit = False
    End If

    If Not mcTree Is Nothing Then
        If mlDemoNo = 2 Then
            cmdDemo2_Click
        ElseIf mlDemoNo = 1 Then
            cmdDemo1_Click
        End If
    End If

End Sub





Private Sub cbxAllowEditing_Click()
' PT
    If Not mcTree Is Nothing Then
        mcTree.LabelEdit(True) = IIf(cbxAllowEditing.value, 0, 1)
    End If
End Sub

Private Sub cbxTestSort_Click()
'PT
    If mlDemoNo Then
        cmdTestSort.Visible = cbxTestSort
    End If
End Sub

Private Sub cmdTestSort_Click()
' PT
Dim cNode As clsNode
    On Error GoTo errH
    If Not mcTree Is Nothing And mbExit = False Then

        Set cNode = mcTree.activeNode
        If cNode.ChildNodes Is Nothing Then
            MsgBox "The selected node does not have any child-nodes", , AppName
        ElseIf cNode.ChildNodes.Count = 1 Then
            MsgBox "The selected node only has one child-node", , AppName
        Else
            SortTest cNode, True
        End If

    End If
    Exit Sub
errH:
    MsgBox Err.Description, , AppName
End Sub

Private Sub SortTest(cNode As clsNode, bRefresh As Boolean)
'PT
    Dim i As Long, j As Long
    Dim sText As String
    Dim cChild As clsNode

    If Not cNode.ChildNodes Is Nothing Then
        For Each cChild In cNode.ChildNodes
            i = i + 1
            sText = ""
            For j = 1 To 10
                sText = sText & Chr(Int(65 + Rnd * 26)) & " "
            Next
            cChild.caption = sText & "   " & vbTab & Right$("000" & i, 4)
        Next
    
        cNode.Sort ndAscending, ndTextCompare
    
        If bRefresh Then
            mcTree.Refresh
        End If
    End If
End Sub

'''''''' Spinners''''''''''''''
Private Sub SpinButton1_Change()
' PT change Indentation during at run-time
'   equivalent to the original Treeview's Indentation
    Dim sngIndent As Single
    
    sngIndent = SpinButton1.value * mcPtPixel
    If Not mbExit Then
        If Not mcTree Is Nothing Then
            sngIndent = SpinButton1.value * mcPtPixel
            mcTree.Indentation = sngIndent
            If mcTree.Indentation <> sngIndent Then    ' tried to change beyond limits
                SpinButton1.value = sngIndent / mcPtPixel
            End If
            UpdateInfoLabel
        End If

    End If
    labIndent = labIndent.Tag & ": " & sngIndent
End Sub

Private Sub SpinButton2_Change()
' PT change NodeHeight during run-time
'    the original Treeview's NodeHeight is governed by the Font
'    in ours the NodeHeight can be set but will "auto-increase" for Font as needs

    Dim sngNodeHt As Single

    sngNodeHt = SpinButton2.value * mcPtPixel
    If Not mbExit Then
        If Not mcTree Is Nothing Then
            mcTree.NodeHeight = sngNodeHt
            If mcTree.NodeHeight <> sngNodeHt Then    ' tried to change beyond limits
                sngNodeHt = mcTree.NodeHeight
                mbExit = True
                SpinButton2.value = sngNodeHt / mcPtPixel
                mbExit = False
            End If
            UpdateInfoLabel
        End If
    End If
    labNodeHeight = labNodeHeight.Tag & ": " & sngNodeHt

End Sub

Private Sub SpinButton3_Change()
' PT When the node labels are created font properties are inherited from
'    the parent container, ie the Frame (frTreeControl)
'    Note the minimum NodeHeight will autosize to the font size, but if decreasing the
'    font size might also want to decrease the nodeHeight
'    This control is disabled at while a treeview demo is displayed

    If Not mbExit Then
        Me.frTreeControl.Font.Size = SpinButton3.value
    End If
    Me.labFont.caption = Me.labFont.Tag & ":   " & SpinButton3.value
End Sub

'/########## Set some Treeview properties with controls on the demo form ##########


'########### Some Read/Write examples behind the buttons #########################

Private Sub cmdFullPath_Click()
' PT similar to the original Treeview's FullPath function
Dim cNode As clsNode
    Dim s As String
    If Not mcTree Is Nothing Then
        Set cNode = mcTree.activeNode
        If cNode Is Nothing Then Exit Sub
        s = mcTree.activeNode.caption
        s = s & vbCr & mcTree.activeNode.FullPath
        MsgBox s, , AppName
    End If

End Sub

Private Sub cmdReset_Click()
' PT change formats and clear Checked
    Dim cNode As clsNode
    If Not mcTree Is Nothing Then
        For Each cNode In mcTree.Nodes
            With cNode
                .BackColor = vbWindowBackground
                .ForeColor = vbWindowText
                .Bold = False
                .Checked = False
            End With
        Next
        Set mcTree.activeNode = mcTree.activeNode
    End If

End Sub

Public Sub cmdRemoveNode_Click()
' PT Remove the selected Node and all its children
'    the approach is slightly different to the original Treeview's

    Dim sMsg As String
    Dim cNode As clsNode
    Dim azReply As String

    On Error GoTo errH
    If mcTree Is Nothing Then Exit Sub

    Set cNode = mcTree.activeNode
    If cNode Is Nothing Then Exit Sub

    sMsg = "Are you sure you want to Remove " & cNode.caption
    If Not cNode.GetChild(-1) Is Nothing Then
        sMsg = sMsg & vbCr & "and all its Child Nodes"
    End If
    sMsg = sMsg & " ?"

    If MsgBox(sMsg, vbOKCancel, AppName) = vbOK Then
        azReply = TellAzquo(cNode, cNode.ParentNode, 0)

        mcTree.NodeRemove cNode

        mcTree.Refresh  ' refresh after all deleted nodes are removed

    End If

    Exit Sub
errH:
    MsgBox Err.Source & vbCr & Err.Description, , AppName
End Sub

Public Sub cmdAddSibling_Click()
' PT add a sibling to the active node
'    there are two approaches -

    Dim cNode As clsNode
    Dim cParent As clsNode
    Dim cSibling As clsNode
    Dim vIcon1, vIcon2
    Static lNewItem As Long

    If mcTree Is Nothing Then Exit Sub

    On Error GoTo errH
    Set cNode = mcTree.activeNode
    If cNode Is Nothing Then Exit Sub

    ' as this is a demo assume we want similar icons (if any)
    vIcon1 = cNode.ImageMain
    vIcon2 = cNode.ImageExpanded
    lNewItem = lNewItem + 1

    '''' Approach-1: use the Node.AddChild method, add as a child to the parent''''''''''''
    ''''             in this example we will sort all the child nodes when done

    '    Set cParent = cNode.ParentNode ' we add to the parent
    '
    '    Set cSibling = cParent.AddChild("MyUniqueSiblingKey" & lNewItem, _
         '                                  "New Sibling of " & cNode.Caption & " #" & lNewItem, _
         '                                  vIcon1, vIcon2)
    '    cSibling.Expanded = False
    '    cParent.Sort

    ''''''''''''''''''end approach-1''''''''''''''''''''''''''''''''''''''''''''''''''''

    ''''Approach-2: use the old style NodeAdd method
    ''''            in this example we can include vRelationship:=tvNext t add after the activenode

    If cNode.ParentNode.caption = "RootHolder" Then
        'We have a root node, add another root node
        Set cSibling = mcTree.NodeAdd(, , _
                                    "MyUniqueSiblingKey" & lNewItem, _
                                    "New name - please edit", _
                                    vIcon1, vIcon2)
    Else
    'mcTree.tvTreeRelationship.tvNext
        Set cSibling = mcTree.NodeAdd(cNode, 2, _
                                    "MyUniqueSiblingKey" & lNewItem, _
                                    "New name - please edit " & cNode.caption & " #" & lNewItem, _
                                    vIcon1, vIcon2)
    End If
    
    cSibling.Expanded = False
    
    ''''''''''''''''' end approach-2''''''''''''''''''''''''''''''''''''''''''''''''''''


    mcTree.Refresh  ' refresh the tree after adding all new nodes

    '  Set mcTree.ActiveNode = cSibling ' could activate the new sibling
    mcTree.ScrollToView cSibling, Top1Bottom2:=2    ' reset scrolltop if necessary to view the new sibling
    Exit Sub

errH:

    MsgBox Err.Source & vbCr & Err.Description, , AppName
    Stop
    Resume
End Sub

Public Sub cmdAddChild_Click()
' PT add a child node to the active node

    Dim cNode As clsNode
    Dim cChild As clsNode
    Dim vIcon1, vIcon2
    Static lNewItem As Long

    On Error GoTo errH
    If mcTree Is Nothing Then Exit Sub

    Set cNode = mcTree.activeNode
    If cNode Is Nothing Then Exit Sub

    If Not cNode.ChildNodes Is Nothing Then
        If cNode.ChildNodes.Count Then
            ' as this is a demo assume we want similar icons to the first child (if any)
            vIcon1 = cNode.ChildNodes(1).ImageMain
            vIcon2 = cNode.ChildNodes(1).ImageExpanded
        End If
    End If

    lNewItem = lNewItem + 1

    Set cChild = cNode.AddChild("Key" & lNewItem, _
                                "New Child of " & cNode.caption & " #" & lNewItem, _
                                vIcon1, vIcon2)

    ''' we could also use the NodeAdd method

    '    Set cChild = mcTree.NodeAdd(cNode, tvChild, "Key" & lNewItem, _
         '                                "New Child of " & cNode.Caption & " #" & lNewItem, _
         '                                vIcon1, vIcon2)

    If Len(vIcon2) = 0 Then
        cChild.Expanded = False    ' don't want to shownded icon
    End If
    cNode.Expanded = True
    mcTree.Refresh    ' refresh the tree after adding all new nodes

    '  Set mcTree.ActiveNode = cChild ' could activate the new child
    mcTree.ScrollToView cChild, 2    ' reset scrolltop if necessary to view the new child
    Exit Sub
errH:
    MsgBox Err.Source & vbCr & Err.Description, , AppName
End Sub

Private Sub cmdSiblings_Click()
' PT similar to the original Treeview's FirstSibling & LastSibling
    Dim s1 As String, s2 As String
    Dim cNode As clsNode

    If Not mcTree Is Nothing Then

        Set cNode = mcTree.activeNode
        If cNode Is Nothing Then Exit Sub
        
        s1 = cNode.caption & vbCr
        Set cNode = cNode.FirstSibling
        If Not cNode Is Nothing Then
            s2 = "First sibling: " & cNode.caption & vbCr
        End If
        Set cNode = mcTree.activeNode.LastSibling
        If Not cNode Is Nothing Then

            s2 = s2 & "Last sibling: " & cNode.caption
        End If
        If Len(s2) = 0 Then s2 = "no siblings"

        MsgBox s1 & s2, , AppName
    End If
End Sub

Private Sub cmdGetData_Click()
Dim lCnt As Long
Dim ws As Worksheet
Dim cRoot As clsNode

    If mcTree Is Nothing Then Exit Sub

    On Error Resume Next
    Set ws = Worksheets("DataDump")
    On Error GoTo 0

    If ws Is Nothing Then
        Set ws = ActiveWorkbook.Worksheets.Add(, ActiveSheet)
        ws.Name = "DataDump"
    Else
        ws.UsedRange.Clear
    End If

    '' Dump all treeview data
    For Each cRoot In mcTree.RootNodes
        GetData1 cRoot, lCnt, 0, ws.Range("A1")
    Next

'    ''' Dump the selected node's + childnodes' data
'    GetData1 mcTree.ActiveNode, 0, 0, ws.Range("A1")

'    '' Dump a snapshot of the visible/expanded nodes
'    For Each cRoot In mcTree.RootNodes
'       GetData2 cRoot, 0, 0, ws.Range("A1")
'    Next

    ws.Activate
End Sub

Sub GetData1(cParent As clsNode, lCt As Long, ByVal lLevel As Long, rng As Range)
' PT, a recursively retrieve the node's decendent data
    Dim cChild As clsNode

    lLevel = lLevel + 1
    lCt = lCt + 1

    rng(lCt, lLevel) = cParent.caption
    If Not cParent.ChildNodes Is Nothing Then
        For Each cChild In cParent.ChildNodes
            GetData1 cChild, lCt, lLevel, rng
        Next
    End If

End Sub

Sub GetData2(cParent As clsNode, lCt As Long, _
             ByVal lLevel As Long, rng As Range)
' PT, a recursively retrieve the node's decendent data
'     but only if visible, a snapshot of the current view
Dim cChild As clsNode
    lLevel = lLevel + 1
    lCt = lCt + 1

    rng(lCt, lLevel) = cParent.caption
    If Not cParent.ChildNodes Is Nothing Then
        If cParent.Expanded Then
            For Each cChild In cParent.ChildNodes
                GetData2 cChild, lCt, lLevel, rng
            Next
        End If
    End If
End Sub

'Sub ShowMyNodes(strSnippet As String, cParent As clsNode, lCt As Long, _
'                ByVal lLevel As Long)
'Dim cChild As clsNode
'
'    lLevel = lLevel + 1
'    lCt = lCt + 1
'
'    If InStr(1, cParent.Caption, strSnippet, vbTextCompare) Then
'        mcTree.ScrollToView cParent
'    End If
'    If Not cParent.ChildNodes Is Nothing Then
'
'            For Each cChild In cParent.ChildNodes
'                ShowMyNodes strSnippet, cChild, lCt, lLevel
'            Next
'
'    End If
'End Sub

Private Sub UpdateInfoLabel()
    If Not mcTree Is Nothing Then
        With mcTree
            'Me.labInfo.caption = "Indentation:  " & .Indentation & vbCr & _
                                 "NodeHeight:   " & .NodeHeight
        End With
    End If
End Sub


Private Sub frTreeControl_MouseUp(ByVal Button As Integer, ByVal Shift As Integer, ByVal X As Single, ByVal Y As Single)
 'Dim Pt As POINTAPI
    Dim ret As Long
    If Button = 2 Then
       az_RgtClkMenu.ShowPopup
    End If
End Sub
